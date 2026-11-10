// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Places split items as list items, each LI &gt; LBody &gt; content right after the previous item's
 * LI. The variants differ only in where the first item's LI comes from.
 */
enum ListPlacement implements ItemPlacement {

    /**
     * Joins the list enclosing the element when it sits in an LBody &gt; LI &gt; L chain, else
     * wraps the element in a new list at its own position.
     */
    JOINING_ENCLOSING {
        @Override
        public PdfStructElem placeFirst(PdfStructElem element, PdfPage page) {
            if (element.getParent() instanceof PdfStructElem parentElem
                    && "LBody".equals(StructTree.mappedRole(parentElem))) {
                if (parentElem.getParent() instanceof PdfStructElem liElem
                        && "LI".equals(StructTree.mappedRole(liElem))
                        && liElem.getParent() instanceof PdfStructElem listElem
                        && "L".equals(StructTree.mappedRole(listElem))) {
                    return element;
                }
                throw new IllegalStateException(
                        "Element's LBody parent is not inside an LI > L chain");
            }
            return wrapInNewList(element, page);
        }
    },

    /**
     * Never joins: a P is wrapped in a new list at its own position, and any other element gets the
     * new list as its sole kid, the first item's content in a new P.
     */
    IN_PLACE {
        @Override
        public PdfStructElem placeFirst(PdfStructElem element, PdfPage page) {
            if ("P".equals(StructTree.mappedRole(element))) {
                return wrapInNewList(element, page);
            }
            ListAssembler assembler = assemblerFor(element);
            PdfStructElem lBody = assembler.newItemBody(assembler.newListIn(element));
            return assembler.newContentIn(lBody, PdfName.P, page);
        }
    };

    @Override
    public PdfStructElem placeNext(PdfStructElem previous, PdfPage page) {
        PdfStructElem li = listItemOf(previous);
        PdfStructElem list = (PdfStructElem) li.getParent();
        ListAssembler assembler = assemblerFor(list);
        PdfStructElem lBody = assembler.newItemBody(list, StructTree.findKidIndex(list, li) + 1);
        return assembler.newContentIn(lBody, previous.getRole(), page);
    }

    @Override
    public void finish(PdfStructElem firstBody) {
        ListItemScribble.refreshCount((PdfStructElem) listItemOf(firstBody).getParent());
    }

    /** Returns the LI holding an item's content element, by way of its LBody. */
    private static PdfStructElem listItemOf(PdfStructElem content) {
        return (PdfStructElem) content.getParent().getParent();
    }

    private static ListAssembler assemblerFor(PdfStructElem elem) {
        return new ListAssembler(StructTree.pdfDocumentFor(elem));
    }

    /** Wraps the element in a new L &gt; LI &gt; LBody chain at its own position. */
    private static PdfStructElem wrapInNewList(PdfStructElem element, PdfPage page) {
        if (!(element.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        int index = StructTree.findKidIndex(container, element);
        ListAssembler assembler = assemblerFor(element);
        PdfStructElem lBody = assembler.newItemBody(assembler.newListAt(container, index));
        assembler.adoptIntoBody(lBody, element, page);
        return element;
    }
}
