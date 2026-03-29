/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MisartifactedTextFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects digit-only text inside /Artifact BMC blocks that was likely mis-artifacted by a previous
 * MistaggedArtifactFix run. Restores them as Reference[Lbl[]] structures.
 */
public class MisartifactedTextCheck extends DocumentCheck {
    private static final Logger logger = LoggerFactory.getLogger(MisartifactedTextCheck.class);

    @Override
    public String name() {
        return "Misartifacted Text Check";
    }

    @Override
    public String description() {
        return "Detects digit-only artifact blocks that should be tagged as Reference labels";
    }

    @Override
    public String passedMessage() {
        return "No mis-artifacted text found";
    }

    @Override
    public String failedMessage() {
        return "Found artifact blocks containing digit-only text that should be tagged";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        IssueList issues = new IssueList();
        Map<PageMcid, PdfStructElem> mcidOwnerMap = buildMcidOwnerMap(ctx);

        for (int pageNum = 1; pageNum <= ctx.doc().getNumberOfPages(); pageNum++) {
            PdfPage page = ctx.doc().getPage(pageNum);
            for (int si = 0; si < page.getContentStreamCount(); si++) {
                PdfStream stream = page.getContentStream(si);
                byte[] bytes = stream.getBytes();
                if (bytes == null || bytes.length == 0) continue;

                try {
                    List<ContentBlock> blocks = parseContentBlocks(bytes, page);
                    findMisartifactedBlocks(blocks, page, pageNum, si, mcidOwnerMap, issues);
                } catch (Exception e) {
                    logger.debug(
                            "Failed to parse content stream {} on page {}: {}",
                            si,
                            pageNum,
                            e.getMessage());
                }
            }
        }
        return issues;
    }

    // == Content stream parsing ======================================

    /** A parsed marked-content block from a content stream. */
    record ContentBlock(boolean isArtifact, Integer mcid, int bmcStart, int bmcEnd, String text) {}

    /** Parses content stream bytes into a sequence of marked-content blocks. */
    List<ContentBlock> parseContentBlocks(byte[] contentBytes, PdfPage page) throws IOException {
        List<ContentBlock> blocks = new ArrayList<>();
        PdfResources resources = page.getResources();
        RandomAccessFileOrArray source =
                new RandomAccessFileOrArray(
                        new RandomAccessSourceFactory().createSource(contentBytes));

        try (PdfTokenizer tokenizer = new PdfTokenizer(source)) {
            PdfCanvasParser parser = new PdfCanvasParser(tokenizer, resources);
            List<PdfObject> operands = new ArrayList<>();

            // State: current marked content block being tracked
            int currentBmcStart = -1;
            int currentBmcEnd = -1;
            boolean inArtifact = false;
            Integer currentMcid = null;
            StringBuilder currentText = new StringBuilder();

            while (true) {
                int opStart = (int) tokenizer.getPosition();
                parser.parse(operands);
                if (operands.isEmpty()) break;
                int opEnd = (int) tokenizer.getPosition();

                if (isBmcOperator(operands)) {
                    currentBmcStart = opStart;
                    currentBmcEnd = opEnd;
                    inArtifact = isArtifactBmc(operands);
                    currentMcid = null;
                    currentText.setLength(0);
                } else if (isBdcOperator(operands)) {
                    currentBmcStart = opStart;
                    currentBmcEnd = opEnd;
                    inArtifact = false;
                    currentMcid = extractMcid(operands, resources, page);
                    currentText.setLength(0);
                } else if (isEmcOperator(operands)) {
                    if (currentBmcStart >= 0) {
                        blocks.add(
                                new ContentBlock(
                                        inArtifact,
                                        currentMcid,
                                        currentBmcStart,
                                        currentBmcEnd,
                                        currentText.toString().trim()));
                    }
                    currentBmcStart = -1;
                    currentBmcEnd = -1;
                    inArtifact = false;
                    currentMcid = null;
                    currentText.setLength(0);
                } else if (inArtifact && isTextOperator(operands)) {
                    appendTextFromOperands(operands, currentText);
                }
            }
        } finally {
            source.close();
        }
        return blocks;
    }

    // == Block analysis ==============================================

    private void findMisartifactedBlocks(
            List<ContentBlock> blocks,
            PdfPage page,
            int pageNum,
            int streamIndex,
            Map<PageMcid, PdfStructElem> mcidOwnerMap,
            IssueList issues) {
        for (int i = 0; i < blocks.size(); i++) {
            ContentBlock block = blocks.get(i);
            if (!block.isArtifact() || block.text().isEmpty()) continue;
            if (!block.text().matches("\\d+")) continue;

            PdfStructElem neighbor = findNeighborElement(blocks, i, pageNum, mcidOwnerMap);
            if (neighbor == null) {
                logger.debug(
                        "Skipping mis-artifacted '{}' on page {}: no neighboring struct element",
                        block.text(),
                        pageNum);
                continue;
            }

            IssueFix fix = new MisartifactedTextFix(pageNum, neighbor, block.text());
            Issue issue =
                    new Issue(
                            IssueType.MISARTIFACTED_TEXT,
                            IssueSev.WARNING,
                            IssueLoc.atPage(pageNum),
                            "Artifact block contains text '"
                                    + block.text()
                                    + "' that may have been mis-artifacted",
                            fix);
            issues.add(issue);
        }
    }

    /** Finds the nearest struct element by looking at neighboring MCID blocks. */
    private PdfStructElem findNeighborElement(
            List<ContentBlock> blocks,
            int artifactIndex,
            int pageNum,
            Map<PageMcid, PdfStructElem> mcidOwnerMap) {
        // Search backward first (preceding content), then forward
        for (int dist = 1; dist <= blocks.size(); dist++) {
            int before = artifactIndex - dist;
            if (before >= 0 && blocks.get(before).mcid() != null) {
                PdfStructElem elem =
                        mcidOwnerMap.get(new PageMcid(pageNum, blocks.get(before).mcid()));
                if (elem != null) return elem;
            }
            int after = artifactIndex + dist;
            if (after < blocks.size() && blocks.get(after).mcid() != null) {
                PdfStructElem elem =
                        mcidOwnerMap.get(new PageMcid(pageNum, blocks.get(after).mcid()));
                if (elem != null) return elem;
            }
        }
        return null;
    }

    // == MCID-to-element reverse map =================================

    record PageMcid(int pageNum, int mcid) {}

    /** Walks the structure tree to build a map from (page, mcid) to owning struct element. */
    static Map<PageMcid, PdfStructElem> buildMcidOwnerMap(DocContext ctx) {
        Map<PageMcid, PdfStructElem> map = new HashMap<>();
        var root = ctx.doc().getStructTreeRoot();
        if (root == null) return map;
        for (var kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                collectMcrOwners(elem, ctx.doc(), map);
            }
        }
        return map;
    }

    private static void collectMcrOwners(
            PdfStructElem elem, PdfDocument doc, Map<PageMcid, PdfStructElem> map) {
        List<PdfMcr> mcrs = StructTree.descendantsOf(elem, PdfMcr.class);
        for (PdfMcr mcr : mcrs) {
            int mcid = mcr.getMcid();
            if (mcid < 0) continue;
            if (mcr.getPageIndirectReference() == null) continue;
            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) continue;
            int pageNum = doc.getPageNumber(pageDict);
            if (pageNum <= 0) continue;
            if (mcr.getParent() instanceof PdfStructElem parent) {
                map.put(new PageMcid(pageNum, mcid), parent);
            }
        }
    }

    // == Operator detection helpers ===================================

    private static boolean isBmcOperator(List<PdfObject> operands) {
        return isOperator(operands, "BMC");
    }

    private static boolean isBdcOperator(List<PdfObject> operands) {
        return isOperator(operands, "BDC");
    }

    private static boolean isEmcOperator(List<PdfObject> operands) {
        return isOperator(operands, "EMC");
    }

    private static boolean isTextOperator(List<PdfObject> operands) {
        return isOperator(operands, "Tj")
                || isOperator(operands, "TJ")
                || isOperator(operands, "'")
                || isOperator(operands, "\"");
    }

    private static boolean isArtifactBmc(List<PdfObject> operands) {
        if (operands.size() < 2) return false;
        PdfObject tag = operands.get(0);
        return tag instanceof PdfName name && "Artifact".equals(name.getValue());
    }

    private static Integer extractMcid(
            List<PdfObject> operands, PdfResources resources, PdfPage page) {
        if (operands.size() < 3) return null;
        PdfObject propsOperand = operands.get(1);

        if (propsOperand instanceof PdfDictionary dict) {
            PdfNumber mcid = dict.getAsNumber(PdfName.MCID);
            return mcid != null ? mcid.intValue() : null;
        }
        if (propsOperand instanceof PdfName name && resources != null) {
            PdfObject resolved = resources.getProperties(name);
            if (resolved instanceof PdfDictionary dict) {
                PdfNumber mcid = dict.getAsNumber(PdfName.MCID);
                return mcid != null ? mcid.intValue() : null;
            }
        }
        return null;
    }

    private static boolean isOperator(List<PdfObject> operands, String op) {
        if (operands.isEmpty()) return false;
        PdfObject last = operands.get(operands.size() - 1);
        return last instanceof PdfLiteral literal && op.equals(literal.toString());
    }

    /** Extracts raw text bytes from Tj/TJ operands using simple ASCII heuristic. */
    private static void appendTextFromOperands(List<PdfObject> operands, StringBuilder sb) {
        for (PdfObject obj : operands) {
            if (obj instanceof PdfString str) {
                sb.append(str.toUnicodeString());
            } else if (obj instanceof PdfArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    PdfObject item = arr.get(i);
                    if (item instanceof PdfString str) {
                        sb.append(str.toUnicodeString());
                    }
                }
            }
        }
    }
}
