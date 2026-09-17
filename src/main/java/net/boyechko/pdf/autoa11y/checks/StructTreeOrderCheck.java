// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.StructTreeOrderFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects structure tree siblings that are out of reading order, which is taken from where content
 * is painted on the page rather than from MCID order. Splitting fixes mint new MCIDs at the end of
 * a page's numbering, so in an already remediated document an element's MCID says nothing about
 * where on the page it sits.
 */
public class StructTreeOrderCheck extends StructTreeCheck {
    private static final Logger logger = LoggerFactory.getLogger(StructTreeOrderCheck.class);

    /** Content whose tops fall within this many points shares a line, read left to right. */
    private static final float LINE_BAND = 4.0f;

    /**
     * Position of an element's earliest content in the document's reading flow: page first, then a
     * band counted down the page, then distance from the left edge. Bands are quantized rather than
     * compared within a tolerance, because a tolerance makes the ordering intransitive and {@code
     * List.sort} rejects such a comparator.
     */
    public record ReadingPosition(int page, int band, float left)
            implements Comparable<ReadingPosition> {
        static final ReadingPosition MAX =
                new ReadingPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, Float.MAX_VALUE);

        @Override
        public int compareTo(ReadingPosition other) {
            int cmp = Integer.compare(page, other.page);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(band, other.band);
            return cmp != 0 ? cmp : Float.compare(left, other.left);
        }
    }

    private final IssueList issues = new IssueList();
    private final Map<Integer, ReadingPosition> cache = new HashMap<>();

    @Override
    public String name() {
        return "Structure Tree Order Check";
    }

    @Override
    public String description() {
        return "Structure tree elements should be in reading order";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        List<PdfStructElem> children = ctx.children();
        DocContext doc = ctx.docCtx();
        if (children.size() >= 2
                && allChildrenLocatable(ctx, children, doc)
                && !isInOrder(children, doc, cache)) {
            IssueFix fix = new StructTreeOrderFix(ctx.node(), cache);
            issues.add(
                    new Issue(
                            IssueType.STRUCT_TREE_OUT_OF_ORDER,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Children are out of reading order",
                            fix));
        }
        // Cache this element's reading position now that all descendants have been visited
        readingPositionOf(ctx.node(), doc, cache);
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Whether every child paints something findable, without which reordering would guess. */
    private boolean allChildrenLocatable(
            StructTreeContext ctx, List<PdfStructElem> children, DocContext doc) {
        for (PdfStructElem child : children) {
            if (readingPositionOf(child, doc, cache).equals(ReadingPosition.MAX)) {
                logger.debug(
                        "Not judging the order of #{}: child #{} paints nothing locatable",
                        StructTree.objNum(ctx.node()),
                        StructTree.objNum(child));
                return false;
            }
        }
        return true;
    }

    /** Checks whether children are already sorted by reading position. */
    public static boolean isInOrder(
            List<PdfStructElem> children, DocContext doc, Map<Integer, ReadingPosition> cache) {
        ReadingPosition prev = null;
        for (PdfStructElem child : children) {
            ReadingPosition key = readingPositionOf(child, doc, cache);
            if (prev != null && key.compareTo(prev) < 0) {
                return false;
            }
            prev = key;
        }
        return true;
    }

    /** Returns the reading position of an element, computing and caching if needed. */
    public static ReadingPosition readingPositionOf(
            PdfStructElem elem, DocContext doc, Map<Integer, ReadingPosition> cache) {
        int objNum = StructTree.objNum(elem);
        if (objNum >= 0) {
            ReadingPosition cached = cache.get(objNum);
            if (cached != null) return cached;
        }

        ReadingPosition key =
                StructTree.descendantsOf(elem, PdfMcr.class).stream()
                        .map(mcr -> positionOf(mcr, doc))
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(ReadingPosition.MAX);

        if (objNum >= 0) {
            cache.put(objNum, key);
        }
        return key;
    }

    /** Returns where an MCR's content is painted, or null when it paints nothing locatable. */
    private static ReadingPosition positionOf(PdfMcr mcr, DocContext doc) {
        int page = StructTree.pageOf(mcr);
        if (page < 1 || page > doc.doc().getNumberOfPages()) {
            return null;
        }

        Map<Integer, Rectangle> bounds =
                doc.getOrComputeMcidBounds(
                        page, () -> Content.extractBoundsForPage(doc.doc().getPage(page)));
        if (bounds.isEmpty()) {
            // Nothing is painted on this page, so MCID order is the only evidence available. The
            // page is compared before the band, so no element ever compares its band against one
            // derived this way on another page.
            return new ReadingPosition(page, mcr.getMcid(), 0f);
        }

        Rectangle rect = bounds.get(mcr.getMcid());
        if (rect == null) {
            return null;
        }
        // PDF user space counts y upward, so negating the top makes higher content sort earlier.
        return new ReadingPosition(page, -Math.round(rect.getTop() / LINE_BAND), rect.getLeft());
    }
}
