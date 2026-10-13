// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Orders structure elements by where their content is painted on the page rather than by MCID.
 * Splitting fixes mint new MCIDs at the end of a page's numbering, so in an already remediated
 * document an element's MCID says nothing about where on the page it sits.
 */
public class ReadingOrder {
    /** Content whose tops fall within this many points shares a line, read left to right. */
    private static final float LINE_BAND = 4.0f;

    /**
     * Position of an element's earliest content in the document's reading flow: page first, then a
     * band counted down the page, then distance from the left edge. Bands are quantized rather than
     * compared within a tolerance, because a tolerance makes the ordering intransitive and {@code
     * List.sort} rejects such a comparator.
     */
    private record Position(int page, int band, float left) implements Comparable<Position> {
        static final Position MAX =
                new Position(Integer.MAX_VALUE, Integer.MAX_VALUE, Float.MAX_VALUE);

        @Override
        public int compareTo(Position other) {
            int cmp = Integer.compare(page, other.page);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(band, other.band);
            return cmp != 0 ? cmp : Float.compare(left, other.left);
        }
    }

    private final PdfDocument doc;
    private final PageContent pages;
    private final Map<Integer, Position> cache = new HashMap<>();

    public ReadingOrder(PdfDocument doc, PageContent pages) {
        this.doc = doc;
        this.pages = pages;
    }

    /**
     * Returns the comparator that puts a parent's children in reading order. A table row is read
     * left to right, so its cells are ordered by their distance from the left edge alone: cells in
     * one row are staggered vertically, a short cell sitting centred beside a tall one, and
     * ordering those down the page would scramble the row.
     */
    public Comparator<PdfStructElem> within(PdfStructElem parent) {
        Comparator<Position> order =
                "TR".equals(StructTree.mappedRole(parent))
                        ? Comparator.comparingInt(Position::page).thenComparing(Position::left)
                        : Comparator.naturalOrder();
        return Comparator.comparing(this::positionOf, order);
    }

    /** Whether an element paints something findable, without which ordering it would guess. */
    public boolean isLocatable(PdfStructElem elem) {
        return !positionOf(elem).equals(Position.MAX);
    }

    /** Returns the reading position of an element, computing and caching if needed. */
    private Position positionOf(PdfStructElem elem) {
        int objNum = StructTree.objNum(elem);
        if (objNum >= 0) {
            Position cached = cache.get(objNum);
            if (cached != null) return cached;
        }

        Position position =
                StructTree.descendantsOf(elem, PdfMcr.class).stream()
                        .map(this::positionOf)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(Position.MAX);

        if (objNum >= 0) {
            cache.put(objNum, position);
        }
        return position;
    }

    /** Returns where an MCR's content is painted, or null when it paints nothing locatable. */
    private Position positionOf(PdfMcr mcr) {
        int page = StructTree.pageOf(mcr);
        if (page < 1 || page > doc.getNumberOfPages()) {
            return null;
        }

        Map<Integer, Rectangle> bounds = pages.bounds(page);
        if (bounds.isEmpty()) {
            // Nothing is painted on this page, so MCID order is the only evidence available. The
            // page is compared before the band, so no element ever compares its band against one
            // derived this way on another page.
            return new Position(page, mcr.getMcid(), 0f);
        }

        Rectangle rect = bounds.get(mcr.getMcid());
        if (rect == null) {
            return null;
        }
        // PDF user space counts y upward, so negating the top makes higher content sort earlier.
        return new Position(page, -Math.round(rect.getTop() / LINE_BAND), rect.getLeft());
    }
}
