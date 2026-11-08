// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
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
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits an element whose marked-content blocks lump several one-line list items into one list item
 * per line. The element's kids must all be MCRs; each block is split at its line boundaries, and
 * every resulting line becomes an item. The MCRs may span pages — each block is located and split
 * in its own page's content stream, and moved or fresh MCRs carry an explicit /Pg where needed.
 *
 * <p>The content stream is spliced at each line boundary (a text-positioning operator following a
 * show operator) so every line becomes its own BDC...EMC block with a fresh MCID, and the structure
 * tree gains one LI &gt; LBody &gt; P per additional line. When the block's BDC opens outside the
 * text objects it marks, splicing inside a BT...ET pair would interleave the marked-content and
 * text-object operators; the splice then relocates the block's boundaries so each text object opens
 * and closes its marked content inside its own BT...ET pair, refusing shapes it cannot relocate
 * legally (painted content between text objects, nested blocks, an item whose lines sit in separate
 * text objects). When the element already sits inside an LBody &gt; LI &gt; L chain the new items
 * join that list after the original item; a bare element is first wrapped in a new L at its own
 * position. The {@link #inPlace} variant never joins: a P is replaced by the new L, and any other
 * element keeps its place and receives the L as its sole kid, the first line in a new P.
 *
 * <p>The spec is an optional safety catch and grouping control: a plain expected line count makes
 * the fix refuse when the actual total differs (e.g. an item wraps over two lines), and per-item
 * line counts (e.g. {@code 1,1,2}) group consecutive lines into multi-line items — across MCR
 * boundaries when needed.
 */
public final class SplitIntoListItemsFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(SplitIntoListItemsFix.class);

    private final PdfStructElem element;
    private final ItemSizes itemSizes;
    private final boolean inPlace;

    /** Splits at every detected line, deriving the item count from the content stream. */
    public SplitIntoListItemsFix(PdfStructElem element) {
        this(element, ItemSizes.eachLine(), false);
    }

    /** Splits into exactly {@code expectedLines} one-line items, refusing on a count mismatch. */
    public SplitIntoListItemsFix(PdfStructElem element, Integer expectedLines) {
        this(element, ItemSizes.expecting(expectedLines), false);
    }

    /** Splits per the spec, building the new list where the element is rather than joining one. */
    public static SplitIntoListItemsFix inPlace(PdfStructElem element, String spec) {
        return new SplitIntoListItemsFix(element, ItemSizes.parse(spec), true);
    }

    /** Splits per the instruction's spec, as parsed by {@link ItemSizes#parse}. */
    public SplitIntoListItemsFix(PdfStructElem element, String spec) {
        this(element, ItemSizes.parse(spec), false);
    }

    private SplitIntoListItemsFix(PdfStructElem element, ItemSizes itemSizes, boolean inPlace) {
        this.element = element;
        this.itemSizes = itemSizes;
        this.inPlace = inPlace;
    }

    @Override
    public int priority() {
        return 5; // before the wrap fix (10), which only sees whole elements
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
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

        PdfStructElem li =
                inPlace
                        ? placeListInPlace(ctx, plans)
                        : ensureListItemChain(ctx, plans.get(0).page());
        PdfStructElem list = (PdfStructElem) li.getParent();
        List<Integer> newMcids = buildItems(ctx, list, li, plans, sizes);
        ListItemScribble.refreshCount(list);

        logger.debug(
                "Split {} MCR(s) on {} into {} items (new MCIDs {})",
                mcrs.size(),
                pages,
                sizes.size(),
                newMcids);
    }

    /**
     * Dry-runs every planned splice against its block's operator geometry so illegal shapes are
     * refused before any structure-tree mutation.
     */
    private void validateSplices(List<McrPlan> plans, List<Integer> sizes) {
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

    /** Returns the element's kids, which must all be marked-content references. */
    private List<PdfMcr> mcrKidsOf(PdfStructElem elem) {
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

    // == Structure tree ==================================================

    /**
     * Returns the LI enclosing the element, wrapping a bare element in a new L &gt; LI &gt; LBody
     * chain at its own position first.
     */
    private PdfStructElem ensureListItemChain(DocContext ctx, PdfPage page) {
        if (element.getParent() instanceof PdfStructElem parentElem
                && "LBody".equals(StructTree.mappedRole(parentElem))) {
            if (parentElem.getParent() instanceof PdfStructElem liElem
                    && "LI".equals(StructTree.mappedRole(liElem))
                    && liElem.getParent() instanceof PdfStructElem listElem
                    && "L".equals(StructTree.mappedRole(listElem))) {
                return liElem;
            }
            throw new IllegalStateException("Element's LBody parent is not inside an LI > L chain");
        }
        return wrapInNewList(ctx, page);
    }

    /**
     * Returns the first LI of a new list built where the element is: a P is wrapped in the list at
     * its own position, and any other element gets the list as its sole kid, its MCRs moved into
     * the first item's new P (updating the plans to the rebound MCRs).
     */
    private PdfStructElem placeListInPlace(DocContext ctx, List<McrPlan> plans) {
        PdfPage page = plans.get(0).page();
        if ("P".equals(StructTree.mappedRole(element))) {
            return wrapInNewList(ctx, page);
        }
        ListAssembler assembler = new ListAssembler(ctx.doc());
        PdfStructElem lBody = assembler.newItemBody(assembler.newListIn(element));
        PdfStructElem p = assembler.newContentIn(lBody, PdfName.P, page);
        for (int i = 0; i < plans.size(); i++) {
            McrPlan plan = plans.get(i);
            plans.set(i, plan.withMcr(moveMcr(plan.mcr(), p)));
        }
        return (PdfStructElem) lBody.getParent();
    }

    /** Wraps the element in a new L &gt; LI &gt; LBody chain at its own position. */
    private PdfStructElem wrapInNewList(DocContext ctx, PdfPage page) {
        if (!(element.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        int index = StructTree.findKidIndex(container, element);

        ListAssembler assembler = new ListAssembler(ctx.doc());
        PdfStructElem list = assembler.newListAt(container, index);
        PdfStructElem lBody = assembler.newItemBody(list);
        assembler.adoptIntoBody(lBody, element, page);
        return (PdfStructElem) lBody.getParent();
    }

    /**
     * Walks the element's lines in reading order, grouping them into items of the given sizes. Each
     * item after the first gets a new LI &gt; LBody &gt; P after the original LI. A line that opens
     * an MCR carries that MCR into its item; a line that starts a new item mid-MCR cuts the block
     * there with a fresh MCID; a line continuing its item within the same MCR stays put, so
     * multi-line items keep a single unsplit block. Returns the new MCIDs in reading order.
     */
    private List<Integer> buildItems(
            DocContext ctx,
            PdfStructElem list,
            PdfStructElem li,
            List<McrPlan> plans,
            List<Integer> sizes) {
        int liIndex = StructTree.findKidIndex(list, li);
        int nextItem = 1;
        List<Integer> newMcids = new ArrayList<>();
        Map<PdfStream, List<Edit>> editsByStream = new LinkedHashMap<>();

        PdfStructElem firstBody = (PdfStructElem) plans.get(0).mcr().getParent();
        PdfStructElem itemBody = firstBody; // item 1's P keeps the first MCR
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
                    itemBody =
                            newItemBody(ctx, list, liIndex + nextItem++, firstBody.getRole(), page);
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

    /**
     * Creates an LI &gt; LBody &gt; content item at the given list position, returning the content.
     */
    private PdfStructElem newItemBody(
            DocContext ctx, PdfStructElem list, int index, PdfName role, PdfPage page) {
        ListAssembler assembler = new ListAssembler(ctx.doc());
        PdfStructElem lBody = assembler.newItemBody(list, index);
        return assembler.newContentIn(lBody, role, page);
    }

    /** Moves an MCR from its current parent to the end of a new one, returning the rebound MCR. */
    private static PdfMcr moveMcr(PdfMcr mcr, PdfStructElem newParent) {
        StructTree.moveKid(mcr, (PdfStructElem) mcr.getParent(), newParent);
        return (PdfMcr) StructTree.kidsOf(newParent).getLast();
    }

    // == Split planning ==================================================

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

    /**
     * Gives an item's content element a further marked-content kid and returns its MCID. Called
     * once per block the splice plan opens, so an item whose lines straddle two text objects ends
     * up owning a reference to the block in each.
     */
    private int mintMcid(PdfStructElem itemBody, PdfPage page, List<Integer> newMcids) {
        PdfMcr mcr = StructTree.addNewMcr(itemBody, page);
        newMcids.add(mcr.getMcid());
        return mcr.getMcid();
    }

    @Override
    public String describe() {
        return "Split lumped block into one list item per line";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), Locus.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "lumped blocks split into list items";
    }
}
