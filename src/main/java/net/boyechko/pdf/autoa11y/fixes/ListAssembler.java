// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * Builds and grows L &gt; LI &gt; LBody list structure for the list-producing fixes, keeping the
 * construction invariants (element wiring, /Pg pinning) in one place.
 */
final class ListAssembler {

    private ListAssembler() {}

    /** Pins /Pg so an element's bare-number MCRs keep resolving their page after a move. */
    static void pinPage(PdfStructElem elem, PdfPage page) {
        if (elem.getPdfObject().get(PdfName.Pg) == null) {
            elem.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        }
    }
}
