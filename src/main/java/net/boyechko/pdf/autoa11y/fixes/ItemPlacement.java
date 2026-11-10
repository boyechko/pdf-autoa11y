// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * Where a {@link LineSplitter} puts the items it cuts from an element: the structure each item's
 * content element lives in. Placements build structure only and keep no state between calls, each
 * reading what it needs from the content elements it is handed; the splitter moves the MCRs.
 */
interface ItemPlacement {

    /**
     * Readies the structure for the first item and returns the content element that holds it. When
     * this is not the element itself, the splitter moves the element's MCRs into it.
     */
    PdfStructElem placeFirst(PdfStructElem element, PdfPage page);

    /**
     * Creates the content element for the next item, of the previous item's role, right after it.
     */
    PdfStructElem placeNext(PdfStructElem previous, PdfPage page);

    /** Finishes the structure once every item is placed, given the first item's content element. */
    default void finish(PdfStructElem firstBody) {}
}
