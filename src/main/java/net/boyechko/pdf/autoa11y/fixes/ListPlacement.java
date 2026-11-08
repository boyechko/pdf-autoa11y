// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Places split items as list items, each LI &gt; LBody &gt; content following the first item's LI.
 * The variants differ only in where the first item's LI comes from.
 */
abstract class ListPlacement implements ItemPlacement {
    protected final ListAssembler assembler;
    private PdfStructElem list;
    private int nextIndex;

    private ListPlacement(PdfDocument doc) {
        this.assembler = new ListAssembler(doc);
    }

    /**
     * Joins the list enclosing the element when it sits in an LBody &gt; LI &gt; L chain, else
     * wraps the element in a new list at its own position.
     */
    static ListPlacement joiningEnclosing(PdfDocument doc) {
        return new JoiningEnclosing(doc);
    }

    /**
     * Never joins: a P is wrapped in a new list at its own position, and any other element gets the
     * new list as its sole kid, the first item's content in a new P.
     */
    static ListPlacement inPlace(PdfDocument doc) {
        return new InPlace(doc);
    }

    @Override
    public final PdfStructElem placeFirst(PdfStructElem element, PdfPage page) {
        PdfStructElem content = firstItemContent(element, page);
        PdfStructElem li = (PdfStructElem) content.getParent().getParent();
        list = (PdfStructElem) li.getParent();
        nextIndex = StructTree.findKidIndex(list, li) + 1;
        return content;
    }

    /** Returns the first item's content element, inside an LBody &gt; LI &gt; L chain. */
    protected abstract PdfStructElem firstItemContent(PdfStructElem element, PdfPage page);

    @Override
    public PdfStructElem placeNext(PdfName role, PdfPage page) {
        PdfStructElem lBody = assembler.newItemBody(list, nextIndex++);
        return assembler.newContentIn(lBody, role, page);
    }

    @Override
    public void finish() {
        ListItemScribble.refreshCount(list);
    }

    /** Wraps the element in a new L &gt; LI &gt; LBody chain at its own position. */
    protected PdfStructElem wrapInNewList(PdfStructElem element, PdfPage page) {
        if (!(element.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        int index = StructTree.findKidIndex(container, element);
        PdfStructElem lBody = assembler.newItemBody(assembler.newListAt(container, index));
        assembler.adoptIntoBody(lBody, element, page);
        return element;
    }

    private static final class JoiningEnclosing extends ListPlacement {
        JoiningEnclosing(PdfDocument doc) {
            super(doc);
        }

        @Override
        protected PdfStructElem firstItemContent(PdfStructElem element, PdfPage page) {
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
    }

    private static final class InPlace extends ListPlacement {
        InPlace(PdfDocument doc) {
            super(doc);
        }

        @Override
        protected PdfStructElem firstItemContent(PdfStructElem element, PdfPage page) {
            if ("P".equals(StructTree.mappedRole(element))) {
                return wrapInNewList(element, page);
            }
            PdfStructElem lBody = assembler.newItemBody(assembler.newListIn(element));
            return assembler.newContentIn(lBody, PdfName.P, page);
        }
    }
}
