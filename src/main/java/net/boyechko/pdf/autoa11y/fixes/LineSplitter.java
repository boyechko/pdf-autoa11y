// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.ContentStream;
import net.boyechko.pdf.autoa11y.document.ContentStream.Edit;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits an element into items of consecutive lines, each item in its own content element as the
 * {@link ItemPlacement} arranges. The element's kids may be MCRs, which may span pages, and
 * elements such as an inline {@code Link} or {@code Span}: each MCR's block is located and split in
 * its own page's content stream, while an element kid moves whole into the item holding its first
 * line, refusing a split that would fall inside it.
 *
 * <p>The content stream is spliced at each line boundary that starts an item (a text-positioning
 * operator following a show operator), so every item gets its own BDC...EMC block with a fresh
 * MCID; lines continuing an item within the same MCR keep a single unsplit block. When the block's
 * BDC opens outside the text objects it marks, the splice relocates the block's boundaries so each
 * text object opens and closes its marked content inside its own BT...ET pair, refusing shapes it
 * cannot relocate legally. Every splice is dry-run before the structure tree is touched.
 */
final class LineSplitter {
    private static final Logger logger = LoggerFactory.getLogger(LineSplitter.class);

    private final PdfStructElem element;
    private final ItemSizes itemSizes;
    private final ItemPlacement placement;

    LineSplitter(PdfStructElem element, ItemSizes itemSizes, ItemPlacement placement) {
        this.element = element;
        this.itemSizes = itemSizes;
        this.placement = placement;
    }

    /**
     * Splits the element, refusing (before any mutation) when its shape cannot be split, and
     * returns each item's content element in reading order.
     */
    List<PdfStructElem> split(DocContext ctx) throws Exception {
        List<KidPlan> plans = planKids(ctx);
        int lineCount = plans.stream().mapToInt(KidPlan::countedLines).sum();
        String pages = pageLabelOf(ctx, plans);
        List<Integer> sizes = itemSizes.resolve(lineCount, pages);
        if (sizes.size() < 2) {
            throw new IllegalStateException(
                    "Splitting on " + pages + " would produce fewer than 2 items");
        }
        validateSplices(plans, sizes);

        PdfStructElem firstBody = placement.placeFirst(element, plans.get(0).page());
        if (!StructTree.isSameElement(firstBody, element)) {
            for (int i = 0; i < plans.size(); i++) {
                plans.set(i, plans.get(i).movedTo(firstBody));
            }
        }
        List<PdfStructElem> items = buildItems(firstBody, plans, sizes, pages);
        placement.finish(firstBody);
        return items;
    }

    /**
     * Plans each kid in reading order, noting whether its first line continues the previous kid's
     * last; refuses an element without kids or with an OBJR among them, which marks no content.
     */
    private List<KidPlan> planKids(DocContext ctx) throws IOException {
        List<IStructureNode> kids = StructTree.kidsOf(element);
        if (kids.isEmpty()) {
            throw new IllegalStateException("Splitting requires an element with content");
        }
        List<KidPlan> plans = new ArrayList<>();
        PdfMcr previous = null;
        for (IStructureNode kid : kids) {
            KidPlan plan =
                    switch (kid) {
                        case PdfObjRef objRef ->
                                throw new IllegalStateException(
                                        "Splitting cannot place an OBJR kid of "
                                                + Format.elem(element));
                        case PdfMcr mcr -> McrPlan.of(ctx, mcr, previous);
                        case PdfStructElem elem -> ElemPlan.of(ctx, elem, previous);
                        default ->
                                throw new IllegalStateException(
                                        "Splitting cannot place kid " + Format.node(kid));
                    };
            plans.add(plan);
            previous = plan.lastMcr();
        }
        return plans;
    }

    /** Returns "page N" or "pages N-M" spanning the plans' pages in reading order. */
    private static String pageLabelOf(DocContext ctx, List<KidPlan> plans) {
        int first = ctx.doc().getPageNumber(plans.get(0).page());
        int last = ctx.doc().getPageNumber(plans.get(plans.size() - 1).page());
        return first == last ? "page " + first : "pages " + first + "-" + last;
    }

    // == Split planning ==================================================

    /**
     * Dry-runs every planned splice against its block's operator geometry so illegal shapes are
     * refused before any structure-tree mutation.
     */
    private static void validateSplices(List<KidPlan> plans, List<Integer> sizes) {
        List<List<Integer>> offsets = spliceOffsetsPerPlan(plans, sizes);
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i) instanceof McrPlan mcrPlan && !offsets.get(i).isEmpty()) {
                ContentStream.blockEditsFor(mcrPlan.plan(), offsets.get(i), segment -> 0);
            }
        }
    }

    /**
     * Returns, per plan, the split offsets that will receive an MCID switch under the sizes;
     * refuses sizes that would start an item partway through an element kid.
     */
    private static List<List<Integer>> spliceOffsetsPerPlan(
            List<KidPlan> plans, List<Integer> sizes) {
        List<List<Integer>> result = new ArrayList<>();
        int itemIndex = 0;
        int linesLeftInItem = sizes.get(0);
        for (KidPlan kid : plans) {
            List<Integer> offsets = new ArrayList<>();
            result.add(offsets);
            for (int line = kid.continuesLine() ? 1 : 0; line < kid.lineSpan(); line++) {
                if (linesLeftInItem == 0) {
                    linesLeftInItem = sizes.get(++itemIndex);
                    if (line > 0) {
                        offsets.add(spliceOffset(kid, line));
                    }
                }
                linesLeftInItem--;
            }
        }
        return result;
    }

    /** Returns the offset where the kid's given line starts, refusing to cut an element kid. */
    private static int spliceOffset(KidPlan kid, int line) {
        return switch (kid) {
            case McrPlan mcrPlan -> mcrPlan.plan().splitOffsets().get(line - 1);
            case ElemPlan elemPlan ->
                    throw new IllegalStateException(
                            "Splitting would cut " + Format.elem(elemPlan.elem()) + " in two");
        };
    }

    /**
     * A kid of the element being split, spanning one or more lines, the first of which may continue
     * the previous kid's last line.
     */
    private sealed interface KidPlan permits McrPlan, ElemPlan {
        PdfPage page();

        boolean continuesLine();

        /** Number of lines the kid touches, a continued first line included. */
        int lineSpan();

        /** The kid's last marked-content reference, against which the next kid's line is read. */
        PdfMcr lastMcr();

        /** Moves the kid to the end of a new parent, returning the plan rebound to it. */
        KidPlan movedTo(PdfStructElem newParent);

        default int countedLines() {
            return lineSpan() - (continuesLine() ? 1 : 0);
        }
    }

    /** An MCR kid, whose block is cut at the line boundaries where items start. */
    private record McrPlan(PdfMcr mcr, PdfPage page, SplitPlan plan, boolean continuesLine)
            implements KidPlan {
        static McrPlan of(DocContext ctx, PdfMcr mcr, PdfMcr previous) throws IOException {
            PdfPage page = StructTree.requirePageOf(mcr);
            return new McrPlan(
                    mcr,
                    page,
                    ContentStream.planLineSplit(page, mcr.getMcid()),
                    Content.continuesLineOf(ctx, previous, mcr));
        }

        @Override
        public int lineSpan() {
            return plan.splitOffsets().size() + 1;
        }

        @Override
        public PdfMcr lastMcr() {
            return mcr;
        }

        @Override
        public McrPlan movedTo(PdfStructElem newParent) {
            return new McrPlan(moveMcr(mcr, newParent), page, plan, continuesLine);
        }
    }

    /** An element kid, such as an inline Link, which moves whole and is never cut. */
    private record ElemPlan(
            PdfStructElem elem, PdfPage page, int lineSpan, boolean continuesLine, PdfMcr lastMcr)
            implements KidPlan {
        static ElemPlan of(DocContext ctx, PdfStructElem elem, PdfMcr previous) throws IOException {
            List<PdfMcr> mcrs = StructTree.descendantsOf(elem, PdfMcr.class);
            if (mcrs.isEmpty()) {
                throw new IllegalStateException(
                        "Splitting cannot place " + Format.elem(elem) + ", which marks no content");
            }
            int lineSpan = 0;
            PdfMcr last = null;
            for (PdfMcr mcr : mcrs) {
                McrPlan plan = McrPlan.of(ctx, mcr, last);
                lineSpan += plan.countedLines();
                last = mcr;
            }
            return new ElemPlan(
                    elem,
                    StructTree.requirePageOf(mcrs.get(0)),
                    lineSpan,
                    Content.continuesLineOf(ctx, previous, mcrs.get(0)),
                    last);
        }

        @Override
        public ElemPlan movedTo(PdfStructElem newParent) {
            StructTree.moveKid(elem, (PdfStructElem) elem.getParent(), newParent);
            return this;
        }
    }

    // == Structure tree ==================================================

    /**
     * Walks the element's lines in reading order, grouping them into items of the given sizes; each
     * item after the first gets a content element from the placement. A line that opens a kid
     * carries that kid into its item; a line that starts a new item mid-MCR cuts the block there
     * with a fresh MCID; a line continuing its item within the same kid stays put. Returns the
     * items' content elements in reading order.
     */
    private List<PdfStructElem> buildItems(
            PdfStructElem firstBody, List<KidPlan> plans, List<Integer> sizes, String pages) {
        List<Integer> newMcids = new ArrayList<>();
        Map<PdfStream, List<Edit>> editsByStream = new LinkedHashMap<>();

        PdfStructElem itemBody = firstBody; // item 1 keeps the first kid
        List<PdfStructElem> items = new ArrayList<>(List.of(firstBody));
        int itemIndex = 0;
        int linesLeftInItem = sizes.get(0);

        for (KidPlan kid : plans) {
            PdfPage page = kid.page();
            List<Integer> splices = new ArrayList<>();
            List<PdfStructElem> bodyPerSegment = new ArrayList<>();
            if (kid.continuesLine()) {
                // The kid's first line is the tail of the previous line, already counted.
                kid.movedTo(itemBody);
                bodyPerSegment.add(itemBody);
            }
            for (int line = kid.continuesLine() ? 1 : 0; line < kid.lineSpan(); line++) {
                boolean startsItem = linesLeftInItem == 0;
                if (startsItem) {
                    linesLeftInItem = sizes.get(++itemIndex);
                    itemBody = placement.placeNext(itemBody, page);
                    items.add(itemBody);
                }
                if (line == 0) {
                    if (kid != plans.get(0)) {
                        kid.movedTo(itemBody);
                    }
                    bodyPerSegment.add(itemBody);
                } else if (startsItem) {
                    splices.add(spliceOffset(kid, line));
                    bodyPerSegment.add(itemBody);
                }
                // A line continuing its item within the same kid needs no action.
                linesLeftInItem--;
            }
            if (kid instanceof McrPlan mcrPlan && !splices.isEmpty()) {
                List<Edit> edits =
                        ContentStream.blockEditsFor(
                                mcrPlan.plan(),
                                splices,
                                segment -> mintMcid(bodyPerSegment.get(segment), page, newMcids));
                editsByStream
                        .computeIfAbsent(mcrPlan.plan().stream(), s -> new ArrayList<>())
                        .addAll(edits);
            }
        }

        for (Map.Entry<PdfStream, List<Edit>> entry : editsByStream.entrySet()) {
            ContentStream.applyEdits(entry.getKey(), entry.getValue());
        }
        logger.debug(
                "Split {} kid(s) on {} into {} items (new MCIDs {})",
                plans.size(),
                pages,
                sizes.size(),
                newMcids);
        return items;
    }

    /** Moves an MCR from its current parent to the end of a new one, returning the rebound MCR. */
    private static PdfMcr moveMcr(PdfMcr mcr, PdfStructElem newParent) {
        StructTree.moveKid(mcr, (PdfStructElem) mcr.getParent(), newParent);
        return (PdfMcr) StructTree.kidsOf(newParent).getLast();
    }

    /**
     * Gives an item's content element a further marked-content kid and returns its MCID. Called
     * once per block the splice plan opens, so an item whose lines straddle two text objects ends
     * up owning a reference to the block in each.
     */
    private static int mintMcid(PdfStructElem itemBody, PdfPage page, List<Integer> newMcids) {
        PdfMcr mcr = StructTree.addNewMcr(itemBody, page);
        newMcids.add(mcr.getMcid());
        return mcr.getMcid();
    }
}
