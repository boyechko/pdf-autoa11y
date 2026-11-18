// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.OptionalLong;
import net.boyechko.pdf.autoa11y.document.DocValue.ObjNum;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains the scribble on a list element with a history of events that modified the list along
 * with the final count of items. Each list-producing fix calls {@link #record} as its last step,
 * specifying the event that occurred.
 *
 * <p>The two kinds of segments behave differently on purpose. The event segment is history: every
 * fix that touches the list leaves one, and they accumulate. The count segment is state: it is
 * replaced each time, so it always reflects the list as it stands and is stamped last so it reads
 * as the outcome of the events before it.
 */
public final class ListItemScribble {
    private static final Logger logger = LoggerFactory.getLogger(ListItemScribble.class);

    private final Tag list;

    private ListItemScribble(Tag list) {
        this.list = list;
    }

    /** The scribble of the given list element. */
    public static ListItemScribble of(PdfStructElem list) {
        return new ListItemScribble(Tag.of(list));
    }

    /**
     * Records {@code event} on the list and refreshes its item count.
     *
     * @param event a tagged event segment naming the calling fix
     */
    public void record(String event) {
        list.stamp(event);
        refresh();
    }

    /**
     * Refreshes the list's item count, recording no event. For a fix whose work was done on an item
     * rather than on the list: the count still has to follow, but an event stamped here would read
     * as having been done to the list itself.
     */
    public void refresh() {
        replace(count());
    }

    /**
     * Refreshes the list's item count alongside the markers (bullets, numbers) counted for it, so a
     * list whose items outnumber its markers shows as much in its scribble.
     */
    public void refresh(long markers) {
        replace(count().withMarkers(markers));
    }

    /** The list's count as it stands, with no markers counted. */
    private Count count() {
        long items = list.children().stream().filter(kid -> "LI".equals(kid.role())).count();
        return Count.of(list.objNum(), items);
    }

    private void replace(Count count) {
        list.unstamp(Count.HEAD);
        list.stamp(count.segment());
        logger.debug(
                "Replacing count segment for {} with '{}'",
                Format.elem(list.elem(), list.page()),
                count.segment());
    }

    /**
     * The count segment as a value: the list it describes, its items, and the markers counted for
     * them when anyone has, rendered as {@code items 4, markers 1 #65848}. The list's number lets a
     * list found in Acrobat's Tags pane, which shows the scribble but not the number, be looked up
     * in a dumped tree.
     *
     * @param list the list's object number, or null for a list without one
     */
    public record Count(ObjNum list, long items, OptionalLong markers) {

        /** Heads the segment, so it can be replaced without matching the rest of its text. */
        public static final String HEAD = "items";

        private static final String MARKERS = "markers";

        /** A count of items alone; a negative object number means the list has none. */
        public static Count of(int objNum, long items) {
            return new Count(objNum >= 0 ? new ObjNum(objNum) : null, items, OptionalLong.empty());
        }

        public static Count of(int objNum, long items, long markers) {
            return of(objNum, items).withMarkers(markers);
        }

        public Count withMarkers(long markers) {
            return new Count(list, items, OptionalLong.of(markers));
        }

        public String segment() {
            StringBuilder segment = new StringBuilder(HEAD).append(' ').append(items);
            markers.ifPresent(
                    count -> segment.append(", ").append(MARKERS).append(' ').append(count));
            if (list != null) {
                segment.append(' ').append(list);
            }
            return segment.toString();
        }
    }
}
