/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfLiteral;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfResources;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Geometry;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts a tagged element to an artifact. */
public class ConvertToArtifact implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ConvertToArtifact.class);
    private static final int P_ARTIFACT = 12; // After doc setup (10), before flatten (15)
    private static final byte[] ARTIFACT_BMC = "/Artifact BMC".getBytes(StandardCharsets.US_ASCII);

    private final PdfStructElem element;

    public ConvertToArtifact(PdfStructElem element) {
        this.element = element;
    }

    @Override
    public int priority() {
        return P_ARTIFACT;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        artifactElement(element, ctx);
    }

    private void artifactElement(PdfStructElem element, DocumentContext ctx) throws IOException {
        IStructureNode parent = element.getParent();
        if (parent == null) {
            logger.debug("Element already has no parent, skipping");
            return;
        }
        if (!isAttachedToParent(element, parent)) {
            logger.debug("Element is already detached from parent, skipping");
            return;
        }

        logger.trace("Artifacting {}", Format.elem(element));

        Map<PdfPage, Set<Integer>> mcidsByPage = collectMcidsByPage(element, ctx);
        removeAnnotationsForElement(element, ctx);
        rewriteMcidsAsArtifacts(mcidsByPage);
        StructureTree.removeFromParent(element, parent);
    }

    private boolean isAttachedToParent(PdfStructElem elem, IStructureNode parent) {
        List<IStructureNode> parentKids = parent.getKids();
        if (parentKids == null || parentKids.isEmpty()) {
            return false;
        }
        for (IStructureNode kid : parentKids) {
            if (kid instanceof PdfStructElem kidElem
                    && StructureTree.isSameElement(kidElem, elem)) {
                return true;
            }
        }
        return false;
    }

    // TODO: This is a duplicate of the method in MistaggedArtifactCheck?
    private Map<PdfPage, Set<Integer>> collectMcidsByPage(PdfStructElem elem, DocumentContext ctx) {
        Map<PdfPage, Set<Integer>> result = new LinkedHashMap<>();
        List<PdfMcr> mcrs = StructureTree.collectMcrs(elem);
        for (PdfMcr mcr : mcrs) {
            int mcid = mcr.getMcid();
            if (mcid < 0) {
                continue;
            }
            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) {
                logger.debug("Skipping MCID {} with no page dictionary", mcid);
                continue;
            }
            int pageNum = ctx.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                logger.debug("Skipping MCID {} with unresolved page dictionary", mcid);
                continue;
            }
            PdfPage page = ctx.doc().getPage(pageNum);
            result.computeIfAbsent(page, ignored -> new LinkedHashSet<>()).add(mcid);
        }
        return result;
    }

    private void rewriteMcidsAsArtifacts(Map<PdfPage, Set<Integer>> mcidsByPage)
            throws IOException {
        for (Map.Entry<PdfPage, Set<Integer>> entry : mcidsByPage.entrySet()) {
            PdfPage page = entry.getKey();
            Set<Integer> targetMcids = entry.getValue();
            if (targetMcids.isEmpty()) {
                continue;
            }

            Set<Integer> rewrittenMcids = new LinkedHashSet<>();
            for (int streamIndex = 0; streamIndex < page.getContentStreamCount(); streamIndex++) {
                PdfStream stream = page.getContentStream(streamIndex);
                StreamRewriteResult result =
                        rewriteTargetedBdcOperators(stream.getBytes(), page, targetMcids);
                if (!result.rewrittenMcids().isEmpty()) {
                    stream.setData(result.rewrittenBytes());
                    rewrittenMcids.addAll(result.rewrittenMcids());
                }
            }

            if (!rewrittenMcids.containsAll(targetMcids)) {
                Set<Integer> missing = new LinkedHashSet<>(targetMcids);
                missing.removeAll(rewrittenMcids);
                int pageNum = page.getDocument().getPageNumber(page);
                throw new IllegalStateException(
                        "Failed to artifact MCIDs " + missing + " on page " + pageNum);
            }
        }
    }

    private StreamRewriteResult rewriteTargetedBdcOperators(
            byte[] contentBytes, PdfPage page, Set<Integer> targetMcids) throws IOException {
        if (contentBytes == null || contentBytes.length == 0) {
            return StreamRewriteResult.unchanged();
        }

        PdfResources resources = page.getResources();
        RandomAccessFileOrArray source =
                new RandomAccessFileOrArray(
                        new RandomAccessSourceFactory().createSource(contentBytes));
        try (PdfTokenizer tokenizer = new PdfTokenizer(source)) {
            PdfCanvasParser parser = new PdfCanvasParser(tokenizer, resources);
            List<PdfObject> operands = new ArrayList<>();
            ByteArrayOutputStream rewritten = new ByteArrayOutputStream(contentBytes.length);
            Set<Integer> rewrittenMcids = new LinkedHashSet<>();
            int lastCopied = 0;

            while (true) {
                int opStart = (int) tokenizer.getPosition();
                parser.parse(operands);
                if (operands.isEmpty()) {
                    break;
                }
                int opEnd = (int) tokenizer.getPosition();

                Integer mcid = targetedMcidForOperation(operands, resources, page, targetMcids);
                if (mcid == null) {
                    continue;
                }

                rewritten.write(contentBytes, lastCopied, opStart - lastCopied);
                rewritten.write(ARTIFACT_BMC);
                rewrittenMcids.add(mcid);
                lastCopied = opEnd;
            }

            if (rewrittenMcids.isEmpty()) {
                return StreamRewriteResult.unchanged();
            }

            rewritten.write(contentBytes, lastCopied, contentBytes.length - lastCopied);
            return new StreamRewriteResult(rewritten.toByteArray(), rewrittenMcids);
        } finally {
            source.close();
        }
    }

    private Integer targetedMcidForOperation(
            List<PdfObject> operands,
            PdfResources resources,
            PdfPage page,
            Set<Integer> targetMcids) {
        if (!isOperator(operands, "BDC") || operands.size() < 3) {
            return null;
        }
        PdfObject propertiesOperand = resolvePropertiesOperand(operands, page);
        Integer mcid = resolveMcid(propertiesOperand, resources);
        if (mcid == null || !targetMcids.contains(mcid)) {
            return null;
        }
        return mcid;
    }

    private PdfObject resolvePropertiesOperand(List<PdfObject> operands, PdfPage page) {
        // BDC uses tag + properties + operator.
        if (operands.size() == 3) {
            return operands.get(1);
        }

        // Some producers emit an indirect reference: /Tag objNum genNum R BDC
        if (operands.size() == 5
                && operands.get(1) instanceof PdfNumber objNum
                && operands.get(2) instanceof PdfNumber
                && isLiteral(operands.get(3), "R")) {
            return page.getDocument().getPdfObject(objNum.intValue());
        }

        return null;
    }

    private static boolean isOperator(List<PdfObject> operands, String op) {
        if (operands.isEmpty()) {
            return false;
        }
        return isLiteral(operands.get(operands.size() - 1), op);
    }

    private static boolean isLiteral(PdfObject object, String literalText) {
        return object instanceof PdfLiteral literal && literalText.equals(literal.toString());
    }

    private Integer resolveMcid(PdfObject propertiesOperand, PdfResources resources) {
        if (propertiesOperand instanceof PdfDictionary dict) {
            return dict.getAsInt(PdfName.MCID);
        }
        if (propertiesOperand instanceof PdfName name && resources != null) {
            PdfObject propertiesObj = resources.getProperties(name);
            if (propertiesObj instanceof PdfDictionary propertiesDict) {
                return propertiesDict.getAsInt(PdfName.MCID);
            }
        }
        return null;
    }

    private record StreamRewriteResult(byte[] rewrittenBytes, Set<Integer> rewrittenMcids) {
        private static StreamRewriteResult unchanged() {
            return new StreamRewriteResult(new byte[0], Set.of());
        }
    }

    /**
     * Walks the structure element's subtree, collects all PdfObjRef children, filters to Link
     * annotations, and hands each one off to {@link #findAndRemoveAnnotation}.
     */
    private void removeAnnotationsForElement(PdfStructElem elem, DocumentContext ctx) {
        List<PdfObjRef> objRefs = StructureTree.collectObjRefs(elem);

        logger.trace("Found {} object ref(s) in {}", objRefs.size(), Format.elem(elem));

        for (PdfObjRef objRef : objRefs) {
            PdfObject refObj = objRef.getReferencedObject();
            if (refObj instanceof PdfDictionary annotDict) {
                PdfName subtype = annotDict.getAsName(PdfName.Subtype);
                if (subtype != null && PdfName.Link.equals(subtype)) {
                    findAndRemoveAnnotation(annotDict, ctx);
                }
            }
        }
    }

    /**
     * Given a single annotation dictionary, resolves which page it lives on (via /P or /Pg), then
     * falls back to a full-document scan. Delegates actual removal to {@link
     * #removeMatchingAnnotationsFromPage}.
     */
    private void findAndRemoveAnnotation(PdfDictionary annotDict, DocumentContext ctx) {
        int annotObjNum =
                annotDict.getIndirectReference() != null
                        ? annotDict.getIndirectReference().getObjNumber()
                        : 0;

        PdfArray targetRect = annotDict.getAsArray(PdfName.Rect);

        PdfDictionary pageDict = annotDict.getAsDictionary(PdfName.P);

        if (pageDict == null) {
            pageDict = annotDict.getAsDictionary(new PdfName("Pg"));
        }

        if (pageDict != null) {
            int pageNum = ctx.doc().getPageNumber(pageDict);
            if (pageNum > 0) {
                PdfPage page = ctx.doc().getPage(pageNum);
                int removed = removeMatchingAnnotationsFromPage(page, annotDict, targetRect);
                if (removed > 0) {
                    return;
                }
                logger.debug(
                        "Annotation not found on expected page {}, will search all pages", pageNum);
            }
        }

        for (int i = 1; i <= ctx.doc().getNumberOfPages(); i++) {
            PdfPage page = ctx.doc().getPage(i);
            if (removeMatchingAnnotationsFromPage(page, annotDict, targetRect) > 0) {
                return;
            }
        }
        logger.warn("Failed to find and remove annotation obj. #{} on any page", annotObjNum);
    }

    /**
     * Iterates annotations on a single page, matches by identity / equality / indirect-ref / rect,
     * removes them, and returns how many were removed (so the caller knows whether to keep
     * searching).
     */
    private int removeMatchingAnnotationsFromPage(
            PdfPage page, PdfDictionary annotDict, PdfArray targetRect) {
        List<PdfAnnotation> annotations = page.getAnnotations();
        int pageNum = page.getDocument().getPageNumber(page);

        List<PdfAnnotation> toRemove = new ArrayList<>();

        for (PdfAnnotation annot : annotations) {
            PdfDictionary annotPdfObj = annot.getPdfObject();
            PdfName annotSubtype = annotPdfObj.getAsName(PdfName.Subtype);

            if (!PdfName.Link.equals(annotSubtype)) {
                continue;
            }

            boolean sameInstance = annotPdfObj == annotDict;
            boolean equalObjects = annotPdfObj.equals(annotDict);
            boolean sameIndirectRef =
                    annotDict.getIndirectReference() != null
                            && annotDict
                                    .getIndirectReference()
                                    .equals(annotPdfObj.getIndirectReference());

            PdfArray annotRect = annotPdfObj.getAsArray(PdfName.Rect);
            boolean sameRect = targetRect != null && Geometry.rectsEqual(targetRect, annotRect);

            if (sameInstance || equalObjects || sameIndirectRef || sameRect) {
                toRemove.add(annot);
            }
        }

        for (PdfAnnotation annot : toRemove) {
            page.removeAnnotation(annot);
            int objNum =
                    annot.getPdfObject().getIndirectReference() != null
                            ? annot.getPdfObject().getIndirectReference().getObjNumber()
                            : 0;
            logger.debug("Removed Link annotation obj. #{} from page {}", objNum, pageNum);
        }

        return toRemove.size();
    }

    @Override
    public String describe() {
        return "Artifacted " + Format.elem(element);
    }

    @Override
    public String describe(DocumentContext ctx) {
        return "Artifacted " + Format.elem(element, ctx);
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        if (otherFix instanceof ConvertToArtifact other) {
            return StructureTree.isDescendantOf(other.element, this.element);
        }
        return false;
    }

    public PdfStructElem getElement() {
        return element;
    }

    @Override
    public String groupLabel() {
        return "elements converted to artifacts";
    }
}
