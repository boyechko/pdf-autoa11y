// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Link;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rebuilds an irregular {@code <TOC>} as a proper TOCI hierarchy.
 *
 * <p>The subtree is first flattened to the content elements that carry the entry text, which
 * discards the incidental difference between a TOC emitted as bare paragraphs and one an earlier
 * pass wrapped as {@code L/LI/LBody}. Those elements are then grouped into visual lines, and each
 * line becomes one {@code TOCI > P} holding the original element.
 *
 * <p>Grouping by line rather than by element is what makes indentation usable: authoring tools
 * routinely split one entry into several tags mid-line, and the trailing tag's left edge is an
 * artifact of where the split fell, not of the entry's level.
 *
 * <p>Nesting depth comes from left-edge indentation. The first entry establishes level 1; the first
 * indent step establishes the ladder, and each later entry either lands on an established rung or
 * opens one rung deeper. An entry whose left edge fits neither is taken as a wrapped continuation
 * of the previous entry and folded into it.
 *
 * <p>Because a nested {@code TOC} may not live inside a {@code TOCI} — the schema allows TOCI only
 * {@code Lbl}, {@code P}, and {@code Span} — a level's children go into a sibling {@code TOC}
 * placed after the {@code TOCI} they belong to.
 */
public final class IrregularTocFix implements IssueFix {

    private static final Logger logger = LoggerFactory.getLogger(IrregularTocFix.class);

    /** Minimum vertical overlap (pt) for two elements to count as sharing a line. */
    private static final float LINE_OVERLAP_MIN = 3.0f;

    /** Maximum left-edge difference (pt) for two entries to sit on the same rung. */
    private static final float LEFT_EDGE_TOLERANCE = 2.0f;

    /** Minimum indent (pt) for an entry to open a deeper rung. */
    private static final float INDENT_THRESHOLD = 10.0f;

    private final PdfStructElem toc;
    private int entryCount;

    public IrregularTocFix(PdfStructElem toc) {
        this.toc = toc;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        List<Item> items = collectItems(ctx);
        if (items.isEmpty()) {
            logger.debug(
                    "TOC #{} has no measurable content; leaving alone", StructTree.objNum(toc));
            return;
        }

        List<Entry> entries = assignLevels(groupIntoEntries(items));
        rebuild(ctx, entries);
        entryCount = entries.size();
    }

    // == Flattening ===================================================

    /** One content element of the TOC, with the page and bounds used to place it. */
    private record Item(PdfStructElem elem, PdfStructElem origin, int pageNum, Rectangle bounds) {}

    private List<Item> collectItems(DocContext ctx) {
        List<Item> items = new ArrayList<>();
        collectInto(toc, ctx, items);
        return items;
    }

    private static void collectInto(PdfStructElem elem, DocContext ctx, List<Item> out) {
        for (PdfStructElem kid : StructTree.childrenOf(elem, PdfStructElem.class)) {
            if (!isEntryContent(kid)) {
                collectInto(kid, ctx, out);
                continue;
            }
            int pageNum = StructTree.pageOf(kid, ctx);
            Rectangle bounds = pageNum > 0 ? Content.getBoundsForElement(kid, ctx, pageNum) : null;
            out.add(new Item(kid, elem, pageNum, bounds));
        }
    }

    /**
     * True for an element that carries entry text itself: a Link (whose own kids are spans of one
     * entry, not separate entries) or any element with no structural children left to descend into.
     */
    private static boolean isEntryContent(PdfStructElem elem) {
        return PdfName.Link.equals(elem.getRole())
                || StructTree.childrenOf(elem, PdfStructElem.class).isEmpty();
    }

    // == Grouping into entries ========================================

    /** The content of one TOC entry: everything on its line, plus any folded continuation. */
    private static final class Entry {
        private final List<Item> items = new ArrayList<>();
        private int level = 1;

        void add(Item item) {
            items.add(item);
        }

        /** True when {@code item} sits on this entry's line, or has no bounds to place it by. */
        boolean sharesLineWith(Item item) {
            Item anchor = items.get(items.size() - 1);
            if (item.bounds() == null || anchor.bounds() == null) {
                return true;
            }
            if (item.pageNum() != anchor.pageNum()) {
                return false;
            }
            float overlap =
                    Math.min(item.bounds().getTop(), anchor.bounds().getTop())
                            - Math.max(item.bounds().getBottom(), anchor.bounds().getBottom());
            return overlap > LINE_OVERLAP_MIN;
        }

        float left() {
            float left = Float.MAX_VALUE;
            for (Item item : items) {
                if (item.bounds() != null) {
                    left = Math.min(left, item.bounds().getLeft());
                }
            }
            return left == Float.MAX_VALUE ? 0f : left;
        }

        int pageNum() {
            return items.get(0).pageNum();
        }

        List<PdfStructElem> links() {
            return items.stream()
                    .map(Item::elem)
                    .filter(elem -> PdfName.Link.equals(elem.getRole()))
                    .toList();
        }

        /** True when every element here is a Link and they all resolve to one destination. */
        boolean isOneLink() {
            return links().size() == items.size() && Link.allShareOneDestination(links());
        }
    }

    private static List<Entry> groupIntoEntries(List<Item> items) {
        List<Entry> entries = new ArrayList<>();
        for (Item item : items) {
            Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
            if (last != null && last.sharesLineWith(item)) {
                last.add(item);
            } else {
                Entry entry = new Entry();
                entry.add(item);
                entries.add(entry);
            }
        }
        joinWrappedLinks(entries);
        return entries;
    }

    /**
     * Joins consecutive entries that turn out to be one link wrapped across lines, which the line
     * grouping cannot see but the shared destination can.
     */
    private static void joinWrappedLinks(List<Entry> entries) {
        for (int i = entries.size() - 1; i > 0; i--) {
            Entry previous = entries.get(i - 1);
            Entry current = entries.get(i);
            List<PdfStructElem> combined = new ArrayList<>(previous.links());
            combined.addAll(current.links());
            if (combined.size() != previous.items.size() + current.items.size()
                    || combined.size() < 2
                    || !Link.allShareOneDestination(combined)) {
                continue;
            }
            previous.items.addAll(current.items);
            entries.remove(i);
        }
    }

    // == Assigning levels =============================================

    /**
     * Walks the entries in reading order, resolving each left edge against a ladder of rungs
     * discovered so far, and folds entries that fit no rung into their predecessor.
     */
    private static List<Entry> assignLevels(List<Entry> entries) {
        List<Entry> kept = new ArrayList<>();
        List<Float> ladder = new ArrayList<>();
        float step = 0f;

        for (Entry entry : entries) {
            if (kept.isEmpty()) {
                ladder.add(entry.left());
                kept.add(entry);
                continue;
            }

            Entry previous = kept.get(kept.size() - 1);
            int rung = rungFor(ladder, entry.left());
            if (rung >= 0) {
                entry.level = rung + 1;
                kept.add(entry);
                continue;
            }

            if (entry.left() < ladder.get(0)) {
                // Outdented past the top rung: the shallowest entry defines level 1.
                ladder.set(0, entry.left());
                kept.add(entry);
                continue;
            }

            float indent = entry.left() - ladder.get(previous.level - 1);
            if (indent >= INDENT_THRESHOLD
                    && (step == 0f || Math.abs(indent - step) <= LEFT_EDGE_TOLERANCE)) {
                if (step == 0f) {
                    step = indent;
                }
                while (ladder.size() > previous.level) {
                    ladder.remove(ladder.size() - 1);
                }
                ladder.add(entry.left());
                entry.level = ladder.size();
                kept.add(entry);
                continue;
            }

            logger.debug(
                    "Folding entry at x={} into the previous entry as a wrapped line",
                    String.format("%.1f", entry.left()));
            previous.items.addAll(entry.items);
        }
        return kept;
    }

    /** Returns the index of the rung {@code left} lands on, or -1 if it lands on none. */
    private static int rungFor(List<Float> ladder, float left) {
        for (int i = 0; i < ladder.size(); i++) {
            if (Math.abs(ladder.get(i) - left) <= LEFT_EDGE_TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    // == Rebuilding ===================================================

    private void rebuild(DocContext ctx, List<Entry> entries) throws Exception {
        Set<PdfStructElem> vacated = new LinkedHashSet<>();
        for (Entry entry : entries) {
            for (Item item : entry.items) {
                vacated.add(item.origin());
            }
        }

        List<PdfStructElem> containers = new ArrayList<>();
        containers.add(toc);

        for (Entry entry : entries) {
            openContainersTo(ctx, containers, entry);
            PdfStructElem container = containers.get(entry.level - 1);

            PdfStructElem toci = newElement(ctx, PdfName.TOCI, entry.pageNum());
            PdfStructElem para = newElement(ctx, PdfName.P, entry.pageNum());
            container.addKid(toci);
            toci.addKid(para);

            for (Item item : entry.items) {
                StructTree.moveKid(item.elem(), item.origin(), para);
            }
            if (entry.isOneLink() && entry.links().size() > 1) {
                new MergeSplitLinksFix(entry.links()).apply(ctx);
            }
        }

        for (PdfStructElem old : vacated) {
            StructTree.pruneEmpty(old);
        }
    }

    /**
     * Grows or trims the container stack so that index {@code level - 1} holds the TOC this entry
     * belongs in. A deeper level opens a nested TOC as a sibling of the TOCI above it.
     */
    private static void openContainersTo(
            DocContext ctx, List<PdfStructElem> containers, Entry entry) {
        while (containers.size() > entry.level) {
            containers.remove(containers.size() - 1);
        }
        while (containers.size() < entry.level) {
            PdfStructElem nested = newElement(ctx, PdfName.TOC, entry.pageNum());
            containers.get(containers.size() - 1).addKid(nested);
            containers.add(nested);
        }
    }

    private static PdfStructElem newElement(DocContext ctx, PdfName role, int pageNum) {
        PdfStructElem elem = new PdfStructElem(ctx.doc(), role);
        if (pageNum > 0) {
            PdfPage page = ctx.doc().getPage(pageNum);
            elem.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        }
        return elem;
    }

    // == Reporting ====================================================

    @Override
    public String describe() {
        return "Nested TOC contents into a TOCI hierarchy";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + " (" + entryCount + " entries)";
    }

    @Override
    public int resolvedItemCount() {
        return entryCount;
    }

    @Override
    public String groupLabel() {
        return "Nested TOC contents into a TOCI hierarchy";
    }
}
