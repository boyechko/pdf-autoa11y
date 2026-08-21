// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * Builds and grows L &gt; LI &gt; LBody list structure for the list-producing fixes, keeping the
 * construction invariants (element wiring, /Pg pinning) in one place.
 */
final class ListAssembler {

    private final PdfDocument doc;

    ListAssembler(PdfDocument doc) {
        this.doc = doc;
    }

    /** Creates an L element appended to the parent and returns it. */
    PdfStructElem newListIn(PdfStructElem parent) {
        PdfStructElem list = new PdfStructElem(doc, PdfName.L);
        parent.addKid(list);
        return list;
    }

    /** Creates an L element inserted into the container at the given index and returns it. */
    PdfStructElem newListAt(PdfStructElem container, int index) {
        PdfStructElem list = new PdfStructElem(doc, PdfName.L);
        container.addKid(index, list);
        return list;
    }

    /** Appends an LI &gt; LBody chain to the list and returns the LBody. */
    PdfStructElem newItemBody(PdfStructElem list) {
        PdfStructElem li = new PdfStructElem(doc, PdfName.LI);
        list.addKid(li);
        return newBodyIn(li);
    }

    /** Inserts an LI &gt; LBody chain into the list at the given index and returns the LBody. */
    PdfStructElem newItemBody(PdfStructElem list, int index) {
        PdfStructElem li = new PdfStructElem(doc, PdfName.LI);
        list.addKid(index, li);
        return newBodyIn(li);
    }

    private PdfStructElem newBodyIn(PdfStructElem li) {
        PdfStructElem lBody = new PdfStructElem(doc, PdfName.LBody);
        li.addKid(lBody);
        return lBody;
    }

    /**
     * Creates a content element of the given role inside the LBody and returns it. /Pg is set on
     * the new element — the immediate parent of future MCRs — so Acrobat preflight accepts them.
     */
    PdfStructElem newContentIn(PdfStructElem lBody, PdfName role, PdfPage page) {
        PdfStructElem content = new PdfStructElem(doc, role);
        content.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        lBody.addKid(content);
        return content;
    }

    /** Moves an element into an LBody, pinning /Pg first so the move cannot orphan its MCRs. */
    void adoptIntoBody(PdfStructElem lBody, PdfStructElem elem, PdfPage page) {
        pinPage(elem, page);
        if (elem.getParent() instanceof PdfStructElem container) {
            container.removeKid(elem);
        }
        lBody.addKid(elem);
    }

    /** Pins /Pg so an element's bare-number MCRs keep resolving their page after a move. */
    static void pinPage(PdfStructElem elem, PdfPage page) {
        if (elem.getPdfObject().get(PdfName.Pg) == null) {
            elem.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        }
    }
}
