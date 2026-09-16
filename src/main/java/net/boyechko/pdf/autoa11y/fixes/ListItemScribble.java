// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Maintains the scribble on a list element with a history of events that modified the list along
 * with the final count of items. Each list-producing fix calls {@link #update} as its last step,
 * specifying the event that occurred.
 *
 * <p>The two kinds of segment behave differently on purpose. The event segment is history: every
 * fix that touches the list leaves one, and they accumulate. The count segment is state: it is
 * replaced each time, so it always reflects the list as it stands and is stamped last so it reads
 * as the outcome of the events before it.
 */
final class ListItemScribble {

    /** Segment tag naming the item count, so it can be replaced without matching its text. */
    private static final String COUNT_TAG = "ITEMS";

    private ListItemScribble() {}

    /**
     * Records {@code event} on the list and refreshes its item count.
     *
     * @param list the list element whose scribble is being stamped
     * @param event a tagged event segment naming the calling fix
     */
    static void update(PdfStructElem list, String event) {
        StructTree.addToolScribble(list, event);
        refreshCount(list);
    }

    /**
     * Refreshes the list's item count, recording no event. For a fix whose work was done on an item
     * rather than on the list: the count still has to follow, but an event stamped here would read
     * as having been done to the list itself.
     */
    static void refreshCount(PdfStructElem list) {
        long count =
                StructTree.childrenOf(list, PdfStructElem.class).stream()
                        .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                        .count();
        StructTree.clearScribbleSegments(list, COUNT_TAG);
        StructTree.addToolScribble(list, countSegment(count));
    }

    static String countSegment(long count) {
        return COUNT_TAG + " " + count;
    }
}
