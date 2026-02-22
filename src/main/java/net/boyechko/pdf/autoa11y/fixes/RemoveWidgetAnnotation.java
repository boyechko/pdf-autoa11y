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

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Removes a pushbutton Widget annotation from the page and its OBJR from the structure tree. */
public class RemoveWidgetAnnotation implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(RemoveWidgetAnnotation.class);
    private static final int P_REMOVE_WIDGET = 12;

    private final PdfDictionary annotDict;
    private final int pageNum;

    public RemoveWidgetAnnotation(PdfDictionary annotDict, int pageNum) {
        this.annotDict = annotDict;
        this.pageNum = pageNum;
    }

    @Override
    public int priority() {
        return P_REMOVE_WIDGET;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        removeAnnotationFromPage(ctx);
        removeObjRefFromStructureTree(ctx);
    }

    private void removeAnnotationFromPage(DocumentContext ctx) {
        PdfPage page = ctx.doc().getPage(pageNum);
        List<PdfAnnotation> annotations = page.getAnnotations();

        for (PdfAnnotation annot : annotations) {
            PdfDictionary annotPdfObj = annot.getPdfObject();
            if (matchesAnnotation(annotPdfObj)) {
                page.removeAnnotation(annot);
                logger.debug(
                        "Removed Widget annotation obj #{} from page {}", objNumber(), pageNum);
                return;
            }
        }
        logger.warn("Widget annotation obj #{} not found on page {}", objNumber(), pageNum);
    }

    private boolean matchesAnnotation(PdfDictionary candidate) {
        if (candidate == annotDict) {
            return true;
        }
        if (annotDict.getIndirectReference() != null
                && annotDict.getIndirectReference().equals(candidate.getIndirectReference())) {
            return true;
        }
        return candidate.equals(annotDict);
    }

    private void removeObjRefFromStructureTree(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }
        PdfStructElem docElem = StructureTree.findDocument(root);
        if (docElem == null) {
            return;
        }
        if (!removeObjRefRecursive(docElem)) {
            logger.warn(
                    "OBJR for Widget annotation obj #{} not found in structure tree", objNumber());
        }
    }

    /**
     * Walks the structure tree looking for a PdfObjRef that references our annotation dictionary.
     * When found, removes it from the parent's /K array.
     */
    private boolean removeObjRefRecursive(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) {
            return false;
        }

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfObjRef objRef) {
                PdfObject refObj = objRef.getReferencedObject();
                if (refObj instanceof PdfDictionary refDict && matchesAnnotation(refDict)) {
                    removeObjRefFromElement(elem, objRef);
                    logger.debug(
                            "Removed OBJR for Widget obj #{} from {} (obj #{})",
                            objNumber(),
                            elem.getRole().getValue(),
                            StructureTree.objNumber(elem));
                    return true;
                }
            } else if (kid instanceof PdfStructElem childElem) {
                if (removeObjRefRecursive(childElem)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Removes a specific OBJR entry from a structure element's /K array. */
    private void removeObjRefFromElement(PdfStructElem elem, PdfObjRef objRef) {
        PdfArray kArray = StructureTree.normalizeKArray(elem);
        if (kArray == null) {
            return;
        }

        PdfObject objRefDict = objRef.getPdfObject();
        for (int i = 0; i < kArray.size(); i++) {
            PdfObject entry = kArray.get(i);
            if (entry == objRefDict
                    || (entry instanceof PdfDictionary dict && dict.equals(objRefDict))) {
                kArray.remove(i);
                return;
            }
            // Also check indirect references
            if (entry.getIndirectReference() != null
                    && objRefDict.getIndirectReference() != null
                    && entry.getIndirectReference().equals(objRefDict.getIndirectReference())) {
                kArray.remove(i);
                return;
            }
        }
    }

    private int objNumber() {
        return annotDict.getIndirectReference() != null
                ? annotDict.getIndirectReference().getObjNumber()
                : 0;
    }

    @Override
    public String describe() {
        return "Removed Widget annotation obj #" + objNumber() + " (p. " + pageNum + ")";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }

    @Override
    public String groupLabel() {
        return "unexpected Widget annotations removed";
    }
}
