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
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for navigating and manipulating the PDF structure tree. */
public final class StructureTree {
    private static final Logger logger = LoggerFactory.getLogger(StructureTree.class);

    private StructureTree() {}

    /** Finds the first child element with the given role. */
    public static PdfStructElem findFirstChild(IStructureNode parent, PdfName role) {
        List<IStructureNode> kids = parent.getKids();
        if (kids == null) return null;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                if (elem.getRole() == role) {
                    return elem;
                }
            }
        }
        return null;
    }

    /** Checks whether a /Pg dictionary refers to the same page. */
    public static boolean isSamePage(PdfDictionary pgDict, PdfPage targetPage) {
        PdfDictionary targetDict = targetPage.getPdfObject();
        if (pgDict.equals(targetDict)) {
            return true;
        }
        if (pgDict.getIndirectReference() != null && targetDict.getIndirectReference() != null) {
            return pgDict.getIndirectReference().equals(targetDict.getIndirectReference());
        }
        return false;
    }

    /** Determines the page number for a structure element, using cache then recursion. */
    public static int determinePageNumber(DocumentContext ctx, PdfStructElem elem) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg != null) {
            int pageNum = ctx.doc().getPageNumber(pg);
            if (pageNum > 0) return pageNum;
        }

        // Check via indirect reference in context cache
        if (elem.getPdfObject().getIndirectReference() != null) {
            int objNum = elem.getPdfObject().getIndirectReference().getObjNumber();
            int pageNum = ctx.getPageNumber(objNum);
            if (pageNum > 0) return pageNum;
        }

        // Recursively check first child with a page
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    int pageNum = determinePageNumber(ctx, childElem);
                    if (pageNum > 0) return pageNum;
                }
            }
        }

        return 0;
    }

    /**
     * Moves an element from one parent to another, updating the /K array and /P reference.
     *
     * @return true if the element was successfully moved
     */
    public static boolean moveElement(
            PdfStructElem fromParent, PdfStructElem elem, PdfStructElem toParent) {
        PdfArray parentKids = fromParent.getPdfObject().getAsArray(PdfName.K);
        if (parentKids == null) return false;

        // Remove from parent's K array
        PdfObject elemObj = elem.getPdfObject();
        boolean removed = false;
        for (int i = 0; i < parentKids.size(); i++) {
            PdfObject obj = parentKids.get(i);
            if (obj == elemObj
                    || (elemObj.getIndirectReference() != null
                            && elemObj.getIndirectReference().equals(obj))) {
                parentKids.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            // Update parent reference (/P) and add to new parent
            elem.getPdfObject().put(PdfName.P, toParent.getPdfObject());
            toParent.addKid(elem);
            logger.debug(
                    "Moved {} to obj #{}",
                    elem.getRole() != null ? elem.getRole().getValue() : "unknown",
                    toParent.getPdfObject().getIndirectReference().getObjNumber());
        }

        return removed;
    }
}
