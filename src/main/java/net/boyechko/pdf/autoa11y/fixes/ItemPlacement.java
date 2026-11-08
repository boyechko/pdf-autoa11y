// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * Where a {@link LineSplitter} puts the items it cuts from an element: the structure each item's
 * content element lives in. Placements build structure only; the splitter moves the MCRs.
 */
interface ItemPlacement {

    /**
     * Readies the structure for the first item and returns the content element that holds it. When
     * this is not the element itself, the splitter moves the element's MCRs into it.
     */
    PdfStructElem placeFirst(PdfStructElem element, PdfPage page);

    /** Creates the content element for the next item, following the previous one. */
    PdfStructElem placeNext(PdfName role, PdfPage page);

    /** Finishes the structure once every item is placed. */
    default void finish() {}
}
