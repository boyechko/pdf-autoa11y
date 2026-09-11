// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Maintains the scribble on a list element, e.g. {@code "__:MERGE lists // LIST 5 items"}. Each
 * list-producing fix calls {@link #update} as its last step, naming what it did.
 *
 * <p>The two kinds of segment behave differently on purpose. The event segment is history: every
 * fix that touches the list leaves one, and they accumulate. The count segment is state: it is
 * replaced each time, so it always reflects the list as it stands and is stamped last so it reads
 * as the outcome of the events before it.
 */
final class ListItemScribble {

    /** Segment tag naming the item count, so it can be replaced without matching its text. */
    private static final String COUNT_TAG = "LIST";

    private ListItemScribble() {}

    /**
     * Records {@code event} on the list and refreshes its item count.
     *
     * @param list the list element whose scribble is being stamped
     * @param event a tagged event segment naming what the calling fix did, e.g. {@code "MERGE
     *     lists"}
     */
    static void update(PdfStructElem list, String event) {
        StructTree.addToolScribble(list, event);

        long count =
                StructTree.childrenOf(list, PdfStructElem.class).stream()
                        .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                        .count();
        StructTree.clearScribbleSegments(list, COUNT_TAG);
        StructTree.addToolScribble(
                list, COUNT_TAG + " " + count + (count == 1 ? " item" : " items"));
    }
}
