// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.Tag;

/**
 * Places split items as siblings: the element keeps the first item, and each further item gets a
 * new element of the same role right after the previous one, pinned to its page.
 */
final class SiblingPlacement implements ItemPlacement {

    @Override
    public PdfStructElem placeFirst(PdfStructElem element, PdfPage page) {
        if (!(element.getParent() instanceof PdfStructElem)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        return element;
    }

    @Override
    public PdfStructElem placeNext(PdfStructElem previous, PdfPage page) {
        PdfStructElem sibling =
                new PdfStructElem(StructTree.pdfDocumentFor(previous), previous.getRole());
        sibling.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        Tag.of(sibling).insertAfter(Tag.of(previous));
        return sibling;
    }
}
