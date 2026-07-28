// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.ContentStream;
import net.boyechko.pdf.autoa11y.document.ContentStream.Edit;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
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
 * position.
 *
 * <p>The spec is an optional safety catch and grouping control: a plain expected line count makes
 * the fix refuse when the actual total differs (e.g. an item wraps over two lines), and per-item
 * line counts (e.g. {@code 1,1,2}) group consecutive lines into multi-line items — across MCR
 * boundaries when needed.
 */
public final class SplitIntoListItemsFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(SplitIntoListItemsFix.class);

    private final PdfStructElem element;
    private final Integer expectedLines;
    private final List<Integer> itemSizes;

    /** Splits at every detected line, deriving the item count from the content stream. */
    public SplitIntoListItemsFix(PdfStructElem element) {
        this(element, (Integer) null);
    }

    /** Splits into exactly {@code expectedLines} one-line items, refusing on a count mismatch. */
    public SplitIntoListItemsFix(PdfStructElem element, Integer expectedLines) {
        this.element = element;
        this.expectedLines = expectedLines;
        this.itemSizes = null;
    }

    /**
     * Splits per the instruction's spec: null/blank derives everything, a plain number is the
     * expected total line count (one item per line), and a comma-separated list gives the line
     * count of each item in order (e.g. {@code 1,1,2} for two one-line items followed by a two-line
     * item), counting lines across all the element's MCRs.
     */
    public SplitIntoListItemsFix(PdfStructElem element, String spec) {
        this.element = element;
        if (spec == null || spec.isBlank()) {
            this.expectedLines = null;
            this.itemSizes = null;
        } else if (spec.matches("\\d+")) {
            this.expectedLines = Integer.valueOf(spec);
            this.itemSizes = null;
        } else if (spec.matches("\\d+(\\s*,\\s*\\d+)+")) {
            this.expectedLines = null;
            this.itemSizes =
                    Arrays.stream(spec.split(","))
                            .map(String::strip)
                            .map(Integer::valueOf)
                            .toList();
        } else {
            throw new IllegalArgumentException(
                    "Bad !SPLIT_LINES spec '" + spec + "'; expected N or N,N,...");
        }
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (expectedLines != null && expectedLines < 2) {
            throw new IllegalArgumentException(
                    "Splitting requires at least 2 lines, got " + expectedLines);
        }
        List<PdfMcr> mcrs = mcrKidsOf(element);

        List<McrPlan> plans = new ArrayList<>();
        int lineCount = 0;
        for (PdfMcr mcr : mcrs) {
            PdfPage page = pageOf(ctx, mcr);
            SplitPlan plan = ContentStream.planLineSplit(page, mcr.getMcid());
            plans.add(new McrPlan(mcr, page, plan));
            lineCount += plan.splitOffsets().size() + 1;
        }
        String pages = pageLabelOf(ctx, plans);
        List<Integer> sizes = resolveItemSizes(lineCount, pages);
        if (sizes.size() < 2) {
            throw new IllegalStateException(
                    "Splitting on " + pages + " would produce fewer than 2 items");
        }
        validateSplices(plans, sizes);

        PdfStructElem li = ensureListItemChain(ctx, plans.get(0).page());
        PdfStructElem list = (PdfStructElem) li.getParent();
        List<Integer> newMcids = buildItems(ctx, list, li, plans, sizes);
        ListItemScribble.update(list);

        logger.debug(
                "Split {} MCR(s) on {} into {} items (new MCIDs {})",
                mcrs.size(),
                pages,
                sizes.size(),
                newMcids);
    }

    /**
     * Verifies the detected total line count against the spec and returns the per-item line counts:
     * the given sizes, or one line per item when no sizes were specified.
     */
    private List<Integer> resolveItemSizes(int lineCount, String pages) {
        if (itemSizes != null) {
            int specTotal = itemSizes.stream().mapToInt(Integer::intValue).sum();
            if (specTotal != lineCount) {
                throw new IllegalStateException(
                        "Item sizes sum to "
                                + specTotal
                                + " lines but found "
                                + lineCount
                                + " on "
                                + pages);
            }
            if (itemSizes.stream().anyMatch(size -> size < 1)) {
                throw new IllegalArgumentException("Item sizes must be at least 1");
            }
            return itemSizes;
        }
        if (expectedLines != null && lineCount != expectedLines) {
            throw new IllegalStateException(
                    "Expected " + expectedLines + " lines but found " + lineCount + " on " + pages);
        }
        return Collections.nCopies(lineCount, 1);
    }

    /**
     * Dry-runs every planned splice against its block's operator geometry so illegal shapes are
     * refused before any structure-tree mutation.
     */
    private void validateSplices(List<McrPlan> plans, List<Integer> sizes) {
        List<List<Integer>> offsets = spliceOffsetsPerPlan(plans, sizes);
        for (int i = 0; i < plans.size(); i++) {
            if (!offsets.get(i).isEmpty()) {
                ContentStream.blockEditsFor(plans.get(i).plan(), offsets.get(i), idx -> 0);
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
            for (int line = 0; line < mcrLines; line++) {
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
        List<IStructureNode> kids = elem.getKids();
        if (kids == null || kids.isEmpty()) {
            throw new IllegalStateException("Splitting requires an element with MCR kids");
        }
        List<PdfMcr> mcrs = new ArrayList<>();
        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfMcr mcr)) {
                throw new IllegalStateException(
                        "Splitting requires an element whose kids are all marked-content"
                                + " references");
            }
            mcrs.add(mcr);
        }
        return mcrs;
    }

    /** Resolves the MCR's page, refusing when it cannot be determined. */
    private static PdfPage pageOf(DocContext ctx, PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        if (pageNum <= 0) {
            throw new IllegalStateException("Cannot resolve the page of MCID " + mcr.getMcid());
        }
        return ctx.doc().getPage(pageNum);
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

        if (!(element.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        int index = StructTree.findKidIndex(container, element);

        PdfStructElem list = new PdfStructElem(ctx.doc(), PdfName.L);
        container.addKid(index, list);
        PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
        list.addKid(li);
        PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        li.addKid(lBody);

        // The element's bare-int MCRs resolve their page via ancestor /Pg; pin the page on the
        // element itself so the move cannot orphan them.
        if (element.getPdfObject().get(PdfName.Pg) == null) {
            element.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        }
        container.removeKid(element);
        lBody.addKid(element);
        return li;
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

        PdfStructElem itemBody = element; // item 1's P; the element keeps the first MCR
        int itemIndex = 0;
        int linesLeftInItem = sizes.get(0);

        for (McrPlan mcrPlan : plans) {
            PdfPage page = mcrPlan.page();
            List<Insertion> insertions = new ArrayList<>();
            int mcrLines = mcrPlan.plan().splitOffsets().size() + 1;
            for (int line = 0; line < mcrLines; line++) {
                boolean startsItem = linesLeftInItem == 0;
                if (startsItem) {
                    linesLeftInItem = sizes.get(++itemIndex);
                    itemBody = newItemBody(ctx, list, liIndex + nextItem++, page);
                }
                if (line == 0) {
                    if (mcrPlan.mcr() != plans.get(0).mcr()) {
                        moveMcr(mcrPlan.mcr(), page, itemBody);
                    }
                } else if (startsItem) {
                    PdfMcr newMcr = newMcrOn(page, itemBody);
                    itemBody.addKid(newMcr);
                    newMcids.add(newMcr.getMcid());
                    insertions.add(
                            new Insertion(
                                    mcrPlan.plan().splitOffsets().get(line - 1), newMcr.getMcid()));
                }
                // A line continuing its item within the same MCR needs no action.
                linesLeftInItem--;
            }
            if (!insertions.isEmpty()) {
                List<Edit> edits =
                        ContentStream.blockEditsFor(
                                mcrPlan.plan(),
                                insertions.stream().map(Insertion::offset).toList(),
                                idx -> insertions.get(idx).mcid());
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

    /** Creates an LI &gt; LBody &gt; P item at the given list position, returning the P. */
    private PdfStructElem newItemBody(DocContext ctx, PdfStructElem list, int index, PdfPage page) {
        PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
        list.addKid(index, li);
        PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        li.addKid(lBody);
        PdfStructElem p = new PdfStructElem(ctx.doc(), element.getRole());
        // Set /Pg on the immediate parent of MCRs so Acrobat preflight accepts them.
        p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        lBody.addKid(p);
        return p;
    }

    /**
     * Moves an MCR from the element into a new parent, re-registering it there. A bare-number MCR
     * resolves its page through the parent's /Pg, so one landing under a parent pinned to a
     * different page is upgraded to an MCR dictionary carrying its own /Pg.
     */
    private void moveMcr(PdfMcr mcr, PdfPage page, PdfStructElem newParent) {
        PdfObject underlying = mcr.getPdfObject();
        element.removeKid(mcr);
        PdfMcr rebound;
        if (!(underlying instanceof PdfNumber num)) {
            rebound = new PdfMcrDictionary((PdfDictionary) underlying, newParent);
        } else if (parentIsPinnedTo(newParent, page)) {
            rebound = new PdfMcrNumber(num, newParent);
        } else {
            PdfDictionary dict = new PdfDictionary();
            dict.put(PdfName.Type, PdfName.MCR);
            dict.put(PdfName.Pg, page.getPdfObject().getIndirectReference());
            dict.put(PdfName.MCID, num);
            rebound = new PdfMcrDictionary(dict, newParent);
        }
        newParent.addKid(rebound);
    }

    /**
     * Creates a fresh-MCID kid for the item body on the given page: a bare number when the body is
     * pinned to that page, an MCR dictionary otherwise.
     */
    private static PdfMcr newMcrOn(PdfPage page, PdfStructElem itemBody) {
        return parentIsPinnedTo(itemBody, page)
                ? new PdfMcrNumber(page, itemBody)
                : new PdfMcrDictionary(page, itemBody);
    }

    /** Checks whether the element's own /Pg refers to the given page. */
    private static boolean parentIsPinnedTo(PdfStructElem elem, PdfPage page) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        return pg != null && StructTree.isSamePage(pg, page);
    }

    // == Split planning ==================================================

    /** A block plan paired with the MCR it belongs to and the page holding its block. */
    private record McrPlan(PdfMcr mcr, PdfPage page, SplitPlan plan) {}

    /** A planned MCID switch: the byte offset where the new MCID's block takes over. */
    private record Insertion(int offset, int mcid) {}

    @Override
    public String describe() {
        return "Split lumped block into one list item per line";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "lumped blocks split into list items";
    }
}
