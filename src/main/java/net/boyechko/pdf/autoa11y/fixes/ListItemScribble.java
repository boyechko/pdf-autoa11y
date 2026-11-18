// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains the scribble on a list element with a history of events that modified the list along
 * with the final count of items. Each list-producing fix calls {@link #update} as its last step,
 * specifying the event that occurred.
 *
 * <p>The two kinds of segments behave differently on purpose. The event segment is history: every
 * fix that touches the list leaves one, and they accumulate. The count segment is state: it is
 * replaced each time, so it always reflects the list as it stands and is stamped last so it reads
 * as the outcome of the events before it.
 */
public final class ListItemScribble {
    private static final Logger logger = LoggerFactory.getLogger(ListItemScribble.class);

    /** Segment tag naming the item count, so it can be replaced without matching its text. */
    private static final String ITEMS_TAG = "items";

    /** Tag naming the marker count, which rides in the item-count segment rather than its own. */
    private static final String MARKERS_TAG = "markers";

    private ListItemScribble() {}

    /**
     * Records {@code event} on the list and refreshes its item count.
     *
     * @param list the list element whose scribble is being stamped
     * @param event a tagged event segment naming the calling fix
     */
    public static void update(PdfStructElem list, String event) {
        Tag.of(list).stamp(event);
        refreshCount(list);
    }

    /**
     * Refreshes the list's item count, recording no event. For a fix whose work was done on an item
     * rather than on the list: the count still has to follow, but an event stamped here would read
     * as having been done to the list itself.
     */
    public static void refreshCount(PdfStructElem list) {
        replaceCount(list, countSegment(itemCountOf(list)));
    }

    /**
     * Refreshes the list's item count alongside the markers (bullets, numbers) counted for it, so a
     * list whose items outnumber its markers shows as much in its scribble.
     */
    public static void refreshCount(PdfStructElem list, long markers) {
        replaceCount(list, countSegment(itemCountOf(list), markers));
    }

    private static void replaceCount(PdfStructElem list, String segment) {
        Tag.of(list).unstamp(ITEMS_TAG);
        Tag.of(list).stamp(segment);
        logger.debug(
                "Replacing count segment for {} with '{}'",
                Format.elem(list, Tag.of(list).page()),
                segment);
    }

    private static long itemCountOf(PdfStructElem list) {
        return Tag.of(list).children().stream().filter(kid -> "LI".equals(kid.role())).count();
    }

    public static String countSegment(long count) {
        return ITEMS_TAG + " " + count;
    }

    public static String countSegment(long count, long markers) {
        return countSegment(count) + ", " + MARKERS_TAG + " " + markers;
    }
}
