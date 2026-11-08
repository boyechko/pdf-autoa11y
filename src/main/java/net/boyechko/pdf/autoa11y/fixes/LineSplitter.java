// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.ContentStream;
import net.boyechko.pdf.autoa11y.document.ContentStream.Edit;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits an element whose kids are all MCRs into items of consecutive lines, each item in its own
 * content element as the {@link ItemPlacement} arranges. The MCRs may span pages: each block is
 * located and split in its own page's content stream.
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

    /** Splits the element, refusing (before any mutation) when its shape cannot be split. */
    void split(DocContext ctx) throws Exception {
        List<PdfMcr> mcrs = mcrKidsOf(element);
        List<McrPlan> plans = new ArrayList<>();
        int lineCount = 0;
        PdfMcr previous = null;
        for (PdfMcr mcr : mcrs) {
            PdfPage page = StructTree.requirePageOf(mcr);
            SplitPlan plan = ContentStream.planLineSplit(page, mcr.getMcid());
            McrPlan mcrPlan =
                    new McrPlan(mcr, page, plan, Content.continuesLineOf(ctx, previous, mcr));
            plans.add(mcrPlan);
            lineCount += mcrPlan.countedLines();
            previous = mcr;
        }
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
                McrPlan plan = plans.get(i);
                plans.set(i, plan.withMcr(moveMcr(plan.mcr(), firstBody)));
            }
        }
        List<Integer> newMcids = buildItems(firstBody, plans, sizes);
        placement.finish();

        logger.debug(
                "Split {} MCR(s) on {} into {} items (new MCIDs {})",
                mcrs.size(),
                pages,
                sizes.size(),
                newMcids);
    }

    /** Returns the element's kids, which must all be marked-content references. */
    private static List<PdfMcr> mcrKidsOf(PdfStructElem elem) {
        List<PdfMcr> mcrs = StructTree.childrenAllMcrOf(elem);
        if (mcrs.isEmpty()) {
            throw new IllegalStateException(
                    "Splitting requires an element whose kids are all marked-content references");
        }
        return mcrs;
    }

    /** Returns "page N" or "pages N-M" spanning the plans' pages in reading order. */
    private static String pageLabelOf(DocContext ctx, List<McrPlan> plans) {
        int first = ctx.doc().getPageNumber(plans.get(0).page());
        int last = ctx.doc().getPageNumber(plans.get(plans.size() - 1).page());
        return first == last ? "page " + first : "pages " + first + "-" + last;
    }

    // == Split planning ==================================================

    /**
     * Dry-runs every planned splice against its block's operator geometry so illegal shapes are
     * refused before any structure-tree mutation.
     */
    private static void validateSplices(List<McrPlan> plans, List<Integer> sizes) {
        List<List<Integer>> offsets = spliceOffsetsPerPlan(plans, sizes);
        for (int i = 0; i < plans.size(); i++) {
            if (!offsets.get(i).isEmpty()) {
                ContentStream.blockEditsFor(plans.get(i).plan(), offsets.get(i), segment -> 0);
            }
        }
    }

    /** Returns, per plan, the split offsets that will receive an MCID switch under the sizes. */
    private static List<List<Integer>> spliceOffsetsPerPlan(
            List<McrPlan> plans, List<Integer> sizes) {
        List<List<Integer>> result = new ArrayList<>();
        int itemIndex = 0;
        int linesLeftInItem = sizes.get(0);
        for (McrPlan mcrPlan : plans) {
            List<Integer> offsets = new ArrayList<>();
            result.add(offsets);
            int mcrLines = mcrPlan.plan().splitOffsets().size() + 1;
            for (int line = mcrPlan.continuesLine() ? 1 : 0; line < mcrLines; line++) {
                if (linesLeftInItem == 0) {
                    linesLeftInItem = sizes.get(++itemIndex);
                    if (line > 0) {
                        offsets.add(mcrPlan.plan().splitOffsets().get(line - 1));
                    }
                }
                linesLeftInItem--;
            }
        }
        return result;
    }

    /**
     * A block plan paired with the MCR it belongs to and the page holding its block, noting whether
     * the block's first line continues the previous MCR's last line.
     */
    private record McrPlan(PdfMcr mcr, PdfPage page, SplitPlan plan, boolean continuesLine) {
        int countedLines() {
            return plan.splitOffsets().size() + (continuesLine ? 0 : 1);
        }

        McrPlan withMcr(PdfMcr rebound) {
            return new McrPlan(rebound, page, plan, continuesLine);
        }
    }

    // == Structure tree ==================================================

    /**
     * Walks the element's lines in reading order, grouping them into items of the given sizes; each
     * item after the first gets a content element from the placement. A line that opens an MCR
     * carries that MCR into its item; a line that starts a new item mid-MCR cuts the block there
     * with a fresh MCID; a line continuing its item within the same MCR stays put. Returns the new
     * MCIDs in reading order.
     */
    private List<Integer> buildItems(
            PdfStructElem firstBody, List<McrPlan> plans, List<Integer> sizes) {
        List<Integer> newMcids = new ArrayList<>();
        Map<PdfStream, List<Edit>> editsByStream = new LinkedHashMap<>();

        PdfStructElem itemBody = firstBody; // item 1 keeps the first MCR
        int itemIndex = 0;
        int linesLeftInItem = sizes.get(0);

        for (McrPlan mcrPlan : plans) {
            PdfPage page = mcrPlan.page();
            List<Integer> splices = new ArrayList<>();
            List<PdfStructElem> bodyPerSegment = new ArrayList<>();
            int mcrLines = mcrPlan.plan().splitOffsets().size() + 1;
            if (mcrPlan.continuesLine()) {
                // The block's first line is the tail of the previous line, already counted.
                moveMcr(mcrPlan.mcr(), itemBody);
                bodyPerSegment.add(itemBody);
            }
            for (int line = mcrPlan.continuesLine() ? 1 : 0; line < mcrLines; line++) {
                boolean startsItem = linesLeftInItem == 0;
                if (startsItem) {
                    linesLeftInItem = sizes.get(++itemIndex);
                    itemBody = placement.placeNext(firstBody.getRole(), page);
                }
                if (line == 0) {
                    if (mcrPlan.mcr() != plans.get(0).mcr()) {
                        moveMcr(mcrPlan.mcr(), itemBody);
                    }
                    bodyPerSegment.add(itemBody);
                } else if (startsItem) {
                    splices.add(mcrPlan.plan().splitOffsets().get(line - 1));
                    bodyPerSegment.add(itemBody);
                }
                // A line continuing its item within the same MCR needs no action.
                linesLeftInItem--;
            }
            if (!splices.isEmpty()) {
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
        return newMcids;
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
