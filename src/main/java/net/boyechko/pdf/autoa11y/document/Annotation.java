// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;

/**
 * Helpers for removing annotation dictionaries from page annotation arrays. Uses raw array
 * manipulation to avoid iText's internal state issues when deleting annotations.
 */
public final class Annotation {

    private Annotation() {}

    /**
     * Removes {@code annotDict} from {@code page}'s /Annots array. Returns true if an entry was
     * found and removed. Clears /Annots entirely when it becomes empty.
     */
    public static boolean removeFromPageAnnots(PdfPage page, PdfDictionary annotDict) {
        PdfDictionary pageDict = page.getPdfObject();
        PdfArray annots = pageDict.getAsArray(PdfName.Annots);
        if (annots == null) {
            return false;
        }
        if (!removeMatchingEntry(annots, annotDict)) {
            return false;
        }
        annots.setModified();
        if (annots.isEmpty()) {
            pageDict.remove(PdfName.Annots);
        }
        pageDict.setModified();
        return true;
    }

    /**
     * Walks every page in {@code doc} and removes {@code annotDict} from the first page's /Annots
     * array that contains it. Returns true if the annotation was removed.
     */
    public static boolean removeFromAnyPage(PdfDocument doc, PdfDictionary annotDict) {
        int pageCount = doc.getNumberOfPages();
        for (int i = 1; i <= pageCount; i++) {
            if (removeFromPageAnnots(doc.getPage(i), annotDict)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the first entry from {@code array} that matches {@code target}. Matches indirect
     * references when both sides have them; otherwise falls back to Java identity and {@code
     * equals} on the underlying dictionary so unflushed, in-memory annotations can also be removed.
     * Intended for page /Annots arrays and AcroForm /Fields /Kids arrays.
     */
    public static boolean removeMatchingEntry(PdfArray array, PdfDictionary target) {
        for (int i = 0; i < array.size(); i++) {
            PdfObject entry = array.get(i);
            if (entry == null) {
                continue;
            }
            if (target.getIndirectReference() != null
                    && entry.getIndirectReference() != null
                    && target.getIndirectReference().equals(entry.getIndirectReference())) {
                array.remove(i);
                return true;
            }
            if (entry == target) {
                array.remove(i);
                return true;
            }
            if (entry instanceof PdfDictionary dict && dict.equals(target)) {
                array.remove(i);
                return true;
            }
        }
        return false;
    }
}
