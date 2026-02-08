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
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts a tagged element to an artifact. */
public class ConvertToArtifact implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ConvertToArtifact.class);
    private static final int P_ARTIFACT = 12; // After doc setup (10), before flatten (15)

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
        IStructureNode parent = element.getParent();
        if (parent == null) {
            logger.debug("Element already has no parent, skipping");
            return;
        }

        removeAssociatedAnnotations(element, ctx);

        if (parent instanceof PdfStructElem parentElem) {
            parentElem.removeKid(element);
            logger.debug(
                    "Removed {} from parent {} obj # {}",
                    element.getRole().getValue(),
                    parentElem.getRole().getValue(),
                    StructureTree.objNumber(parentElem));
        } else if (parent instanceof PdfStructTreeRoot root) {
            PdfObject kObj = root.getPdfObject().get(PdfName.K);
            if (kObj instanceof PdfArray kArray) {
                kArray.remove(element.getPdfObject());
                if (element.getPdfObject().getIndirectReference() != null) {
                    kArray.remove(element.getPdfObject().getIndirectReference());
                }
                logger.debug("Removed {} from structure tree root", element.getRole().getValue());
            }
        }
    }

    private void removeAssociatedAnnotations(PdfStructElem elem, DocumentContext ctx) {
        List<PdfObjRef> objRefs = new ArrayList<>();
        collectObjRefs(elem, objRefs);

        logger.debug(
                "Found {} OBJR(s) in element {} (obj #{})",
                objRefs.size(),
                elem.getRole() != null ? elem.getRole().getValue() : "unknown",
                StructureTree.objNumber(elem));

        for (PdfObjRef objRef : objRefs) {
            PdfObject refObj = objRef.getReferencedObject();
            if (refObj instanceof PdfDictionary annotDict) {
                PdfName subtype = annotDict.getAsName(PdfName.Subtype);
                if (subtype != null && PdfName.Link.equals(subtype)) {
                    removeAnnotationFromPage(annotDict, ctx);
                }
            }
        }
    }

    private void collectObjRefs(PdfStructElem elem, List<PdfObjRef> objRefs) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) {
            return;
        }

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfObjRef objRef) {
                objRefs.add(objRef);
            } else if (kid instanceof PdfStructElem childElem) {
                collectObjRefs(childElem, objRefs);
            }
        }
    }

    private void removeAnnotationFromPage(PdfDictionary annotDict, DocumentContext ctx) {
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
                int removed = removeAnnotationAndDuplicates(page, annotDict, targetRect);
                if (removed > 0) {
                    return;
                }
                logger.debug(
                        "Annotation not found on expected page {}, will search all pages", pageNum);
            }
        }

        for (int i = 1; i <= ctx.doc().getNumberOfPages(); i++) {
            PdfPage page = ctx.doc().getPage(i);
            if (removeAnnotationAndDuplicates(page, annotDict, targetRect) > 0) {
                return;
            }
        }
        logger.warn("Failed to find and remove annotation obj #{} on any page", annotObjNum);
    }

    private int removeAnnotationAndDuplicates(
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
            boolean sameRect = targetRect != null && rectsEqual(targetRect, annotRect);

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
            logger.debug("Removed Link annotation obj #{} from page {}", objNum, pageNum);
        }

        return toRemove.size();
    }

    // TODO: Move to a utility class
    private boolean rectsEqual(PdfArray rect1, PdfArray rect2) {
        if (rect1 == null || rect2 == null) {
            return false;
        }
        if (rect1.size() != 4 || rect2.size() != 4) {
            return false;
        }

        // Compare with small tolerance for floating point differences
        double tolerance = 0.5;
        for (int i = 0; i < 4; i++) {
            double v1 = rect1.getAsNumber(i).doubleValue();
            double v2 = rect2.getAsNumber(i).doubleValue();
            if (Math.abs(v1 - v2) > tolerance) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String describe() {
        String role = element.getRole() != null ? element.getRole().getValue() : "unknown";
        int objNum = StructureTree.objNumber(element);
        return "Artifacted " + role + " obj #" + objNum;
    }

    @Override
    public String describe(DocumentContext ctx) {
        String role = element.getRole() != null ? element.getRole().getValue() : "unknown";
        int objNum = StructureTree.objNumber(element);
        int pageNum = ctx.getPageNumber(objNum);
        String pageInfo = (pageNum > 0) ? " (p. " + pageNum + ")" : "";
        return "Artifacted " + role + " obj #" + objNum + pageInfo;
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        if (otherFix instanceof ConvertToArtifact other) {
            return isDescendantOf(other.element, this.element);
        }
        return false;
    }

    // TODO: Move to a utility class
    private boolean isDescendantOf(PdfStructElem candidate, PdfStructElem ancestor) {
        IStructureNode parent = candidate.getParent();
        while (parent != null) {
            if (parent instanceof PdfStructElem parentElem
                    && isSameStructElem(parentElem, ancestor)) {
                return true;
            }
            if (parent instanceof PdfStructElem parentElem) {
                parent = parentElem.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    // TODO: Move to a utility class
    private boolean isSameStructElem(PdfStructElem a, PdfStructElem b) {
        if (a == b) {
            return true;
        }
        PdfDictionary aDict = a.getPdfObject();
        PdfDictionary bDict = b.getPdfObject();
        if (aDict == bDict) {
            return true;
        }
        PdfIndirectReference aRef = aDict.getIndirectReference();
        PdfIndirectReference bRef = bDict.getIndirectReference();
        return aRef != null && aRef.equals(bRef);
    }

    public PdfStructElem getElement() {
        return element;
    }

    @Override
    public String groupLabel() {
        return "elements converted to artifacts";
    }
}
