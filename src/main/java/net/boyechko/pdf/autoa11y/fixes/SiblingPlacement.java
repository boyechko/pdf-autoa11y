// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Places split items as siblings: the element keeps the first item, and each further item gets a
 * new element of the same role right after the previous one, pinned to its page.
 */
final class SiblingPlacement implements ItemPlacement {
    private final PdfDocument doc;
    private final List<PdfStructElem> placed = new ArrayList<>();
    private PdfStructElem parent;
    private int nextIndex;

    SiblingPlacement(PdfDocument doc) {
        this.doc = doc;
    }

    @Override
    public PdfStructElem placeFirst(PdfStructElem element, PdfPage page) {
        if (!(element.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        parent = container;
        nextIndex = StructTree.findKidIndex(parent, element) + 1;
        placed.add(element);
        return element;
    }

    @Override
    public PdfStructElem placeNext(PdfName role, PdfPage page) {
        PdfStructElem sibling = new PdfStructElem(doc, role);
        sibling.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        parent.addKid(nextIndex++, sibling);
        placed.add(sibling);
        return sibling;
    }

    /** Returns the elements holding the items, in reading order. */
    List<PdfStructElem> placed() {
        return placed;
    }
}
