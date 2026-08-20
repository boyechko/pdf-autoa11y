// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
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
 * Splits an element that begins in the middle of a list item: its opening lines finish the item its
 * predecessor began, and the deeper-bulleted lines that follow are that item's sublist. A bullet
 * marks where an item starts, never where one ends, so an element covering bullets need not own the
 * lines before the first of them — treating its own boundary as an item boundary would hand those
 * lines to the wrong item.
 *
 * <p>The predecessor is wrapped in an {@code L > LI > LBody} chain unless it already has one, the
 * element's opening lines move in beside it, and the rest of the block is spliced off into a fresh
 * sublist there. Splitting that sublist into one item per bullet is delegated to {@link
 * SplitIntoListItemsFix}, which sees an ordinary bare element inside its own list.
 *
 * @see SplitIntoListItemsFix
 */
public final class SplitIntoSublistFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(SplitIntoSublistFix.class);

    private final PdfStructElem element;
    private final PdfStructElem predecessor;
    private final int leadingLines;
    private final String itemSpec;
    private final PdfStructElem joinInto;

    /** Folds into a bare predecessor, wrapping it in a list of its own. */
    public SplitIntoSublistFix(
            PdfStructElem element, PdfStructElem predecessor, int leadingLines, String itemSpec) {
        this(element, predecessor, leadingLines, itemSpec, null);
    }

    /**
     * @param element the element whose first {@code leadingLines} lines continue the previous item
     * @param predecessor the element that began that item
     * @param leadingLines how many of the element's opening lines belong to the predecessor's item
     * @param itemSpec per-item line counts for the sublist, as {@link SplitIntoListItemsFix} takes
     * @param joinInto the list the rebuilt item opens, or null to wrap the predecessor in a new one
     */
    public SplitIntoSublistFix(
            PdfStructElem element,
            PdfStructElem predecessor,
            int leadingLines,
            String itemSpec,
            PdfStructElem joinInto) {
        this.element = element;
        this.predecessor = predecessor;
        this.leadingLines = leadingLines;
        this.itemSpec = itemSpec;
        this.joinInto = joinInto;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfMcr mcr = onlyMcrKidOf(element);
        PdfPage page = pageOf(ctx, mcr);
        SplitPlan plan = ContentStream.planLineSplit(page, mcr.getMcid());
        int lineCount = plan.splitOffsets().size() + 1;
        if (leadingLines < 1 || leadingLines >= lineCount) {
            throw new IllegalStateException(
                    "Cannot keep "
                            + leadingLines
                            + " leading line(s) of a "
                            + lineCount
                            + "-line block");
        }

        PdfStructElem itemBody = ensureItemBody(ctx, page);
        moveAfterPredecessor(itemBody, page);
        PdfStructElem tail = buildSublistTail(ctx, itemBody, page);

        PdfMcr tailMcr = new PdfMcrNumber(page, tail);
        tail.addKid(tailMcr);
        List<Edit> edits =
                ContentStream.blockEditsFor(
                        plan,
                        List.of(plan.splitOffsets().get(leadingLines - 1)),
                        index -> tailMcr.getMcid());
        ContentStream.applyEdits(plan.stream(), edits);

        new SplitIntoListItemsFix(tail, itemSpec).apply(ctx);

        logger.debug(
                "Folded {} leading line(s) of #{} into #{}'s item and nested {} line(s) as its"
                        + " sublist",
                leadingLines,
                StructTree.objNum(element),
                StructTree.objNum(predecessor),
                lineCount - leadingLines);
    }

    @Override
    public String describe() {
        return "Folded continuation lines into the previous item and nested the rest as a sublist";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "mid-item blocks folded into the previous item";
    }

    // == Structure tree ==================================================

    /**
     * Returns the LBody of the predecessor's list item. A predecessor already inside an LBody keeps
     * it; otherwise the item is built as the opening item of {@code joinInto}, or, with no list to
     * join, as a new L &gt; LI &gt; LBody chain at the predecessor's own position.
     */
    private PdfStructElem ensureItemBody(DocContext ctx, PdfPage page) {
        if (predecessor.getParent() instanceof PdfStructElem parentElem
                && "LBody".equals(StructTree.mappedRole(parentElem))) {
            return parentElem;
        }

        if (!(predecessor.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Predecessor has no structure-element parent");
        }

        PdfStructElem list;
        PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
        if (joinInto != null) {
            // The item this element continues opens a list already tagged after it, so it
            // belongs at that list's head rather than in a second list beside it.
            list = joinInto;
            list.addKid(0, li);
        } else {
            list = new PdfStructElem(ctx.doc(), PdfName.L);
            container.addKid(StructTree.findKidIndex(container, predecessor), list);
            list.addKid(li);
        }
        PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        li.addKid(lBody);

        pinPage(predecessor, page);
        container.removeKid(predecessor);
        lBody.addKid(predecessor);
        ListItemScribble.update(list, "continued item, ");
        return lBody;
    }

    /** Moves the element out of its container to sit beside the predecessor in its item. */
    private void moveAfterPredecessor(PdfStructElem itemBody, PdfPage page) {
        if (!(element.getParent() instanceof PdfStructElem container)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        pinPage(element, page);
        container.removeKid(element);
        itemBody.addKid(StructTree.findKidIndex(itemBody, predecessor) + 1, element);
    }

    /** Builds an empty L &gt; LI &gt; LBody &gt; P sublist in the item body and returns the P. */
    private PdfStructElem buildSublistTail(DocContext ctx, PdfStructElem itemBody, PdfPage page) {
        PdfStructElem sublist = new PdfStructElem(ctx.doc(), PdfName.L);
        itemBody.addKid(sublist);
        PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
        sublist.addKid(li);
        PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        li.addKid(lBody);
        PdfStructElem tail = new PdfStructElem(ctx.doc(), element.getRole());
        pinPage(tail, page);
        lBody.addKid(tail);
        return tail;
    }

    /** Pins /Pg so an element's bare-number MCRs keep resolving their page after a move. */
    private static void pinPage(PdfStructElem elem, PdfPage page) {
        if (elem.getPdfObject().get(PdfName.Pg) == null) {
            elem.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        }
    }

    // == Marked content ==================================================

    /** Returns the element's sole kid, which must be a single marked-content reference. */
    private static PdfMcr onlyMcrKidOf(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids != null && kids.size() == 1 && kids.get(0) instanceof PdfMcr mcr) {
            return mcr;
        }
        throw new IllegalStateException(
                "Splitting off a sublist requires an element with a single marked-content kid");
    }

    /** Resolves the MCR's page, refusing when it cannot be determined. */
    private static PdfPage pageOf(DocContext ctx, PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        if (pageNum <= 0) {
            throw new IllegalStateException("Cannot resolve the page of MCID " + mcr.getMcid());
        }
        return ctx.doc().getPage(pageNum);
    }
}
