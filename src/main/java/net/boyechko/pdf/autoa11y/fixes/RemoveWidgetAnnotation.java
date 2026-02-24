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
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes a pushbutton Widget annotation from the page, AcroForm, and structure tree. Uses raw PDF
 * array manipulation throughout to avoid iText's internal state management which can produce
 * "Flushed object contains indirect reference which is free" errors when removing annotations via
 * the high-level API.
 */
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
    public void apply(DocContext ctx) throws Exception {
        removeFromPageAnnots(ctx);
        removeFromAcroForm(ctx);
        removeObjRefFromStructureTree(ctx);
    }

    /** Removes this annotation from the page's /Annots array directly. */
    private void removeFromPageAnnots(DocContext ctx) {
        PdfDictionary pageDict = ctx.doc().getPage(pageNum).getPdfObject();
        PdfArray annots = pageDict.getAsArray(PdfName.Annots);
        if (annots == null) {
            return;
        }

        if (removeMatchingEntry(annots)) {
            annots.setModified();
            if (annots.isEmpty()) {
                pageDict.remove(PdfName.Annots);
            }
            pageDict.setModified();
            logger.debug("Removed Widget annotation obj. #{} from page {}", objNumber(), pageNum);
        } else {
            logger.warn("Widget annotation obj. #{} not found on page {}", objNumber(), pageNum);
        }
    }

    /**
     * Removes this widget from the AcroForm field hierarchy. For child widgets (with /Parent),
     * removes from the parent's /Kids. For merged field+widget dicts, removes from /Fields. Cleans
     * up empty parent fields and the AcroForm itself.
     */
    private void removeFromAcroForm(DocContext ctx) {
        PdfDictionary catalog = ctx.doc().getCatalog().getPdfObject();
        PdfDictionary acroForm = catalog.getAsDictionary(PdfName.AcroForm);
        if (acroForm == null) {
            return;
        }
        PdfArray fields = acroForm.getAsArray(PdfName.Fields);
        if (fields == null) {
            return;
        }

        PdfDictionary parent = annotDict.getAsDictionary(PdfName.Parent);
        if (parent != null) {
            // Child widget: remove from parent's /Kids
            PdfArray kids = parent.getAsArray(PdfName.Kids);
            if (kids != null && removeMatchingEntry(kids)) {
                kids.setModified();
                parent.setModified();
            }
            // If parent has no remaining kids, remove parent from /Fields
            if (kids == null || kids.isEmpty()) {
                removeMatchingEntry(fields, parent);
                fields.setModified();
            }
        } else {
            // Merged field+widget: remove directly from /Fields
            if (removeMatchingEntry(fields)) {
                fields.setModified();
            }
        }

        if (fields.isEmpty()) {
            catalog.remove(PdfName.AcroForm);
            catalog.setModified();
            logger.debug("Removed empty AcroForm from catalog");
        }
    }

    /**
     * Removes the entry matching this annotation's indirect reference from the array. Returns true
     * if an entry was found and removed.
     */
    private boolean removeMatchingEntry(PdfArray array) {
        return removeMatchingEntry(array, annotDict);
    }

    /**
     * Removes an entry from the array whose indirect reference matches the target dictionary.
     * Checks both raw (non-dereferenced) and resolved entries to handle iText's internal storage.
     */
    private boolean removeMatchingEntry(PdfArray array, PdfDictionary target) {
        if (target.getIndirectReference() == null) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            // Check resolved (dereferenced) entry
            PdfObject resolved = array.get(i);
            if (resolved != null
                    && target.getIndirectReference().equals(resolved.getIndirectReference())) {
                array.remove(i);
                return true;
            }
        }
        return false;
    }

    private void removeObjRefFromStructureTree(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }
        PdfStructElem docElem = StructTree.findDocument(root);
        if (docElem == null) {
            return;
        }
        if (!removeObjRefRecursive(docElem)) {
            logger.debug("OBJR for Widget obj. #{} not found in structure tree", objNumber());
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
                            "Removed OBJR for Widget obj. #{} from {} (obj. #{})",
                            objNumber(),
                            elem.getRole().getValue(),
                            StructTree.objNum(elem));
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
        PdfArray kArray = StructTree.normalizeKArray(elem);
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
            if (entry.getIndirectReference() != null
                    && objRefDict.getIndirectReference() != null
                    && entry.getIndirectReference().equals(objRefDict.getIndirectReference())) {
                kArray.remove(i);
                return;
            }
        }
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

    private int objNumber() {
        return annotDict.getIndirectReference() != null
                ? annotDict.getIndirectReference().getObjNumber()
                : 0;
    }

    @Override
    public String describe() {
        return "Removed Widget annotation "
                + Format.objNum(objNumber())
                + " ("
                + Format.page(pageNum)
                + ")";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }

    @Override
    public String groupLabel() {
        return "unexpected Widget annotations removed";
    }
}
