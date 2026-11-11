// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits an element that begins in the middle of a list item and opens more items at that item's
 * own indent. Its opening lines finish the item a previous item of the same list began — typically
 * across a page break — and the bulleted lines after them are that list's next items, not a
 * sublist. A bullet marks where an item starts, never where one ends, so splitting on the bullets
 * alone would leave the opening lines standing as an item of their own.
 *
 * <p>Splitting into one item per bullet is delegated to {@link SplitIntoListItemsFix}, with the
 * opening lines given as its first item; that item's content is then folded into the element that
 * began it and the item it vacated is pruned, so the continuation reads as one paragraph rather
 * than a paragraph break mid-sentence.
 *
 * @see SplitIntoSublistFix for the case where the following bullets are indented into a sublist
 */
public final class FoldIntoPreviousItemFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(FoldIntoPreviousItemFix.class);

    private final PdfStructElem element;
    private final int leadingLines;
    private final String itemSpec;

    /** The list the items ended up in, kept for the report: the element itself is pruned. */
    private PdfStructElem list;

    /**
     * @param element the element whose first {@code leadingLines} lines continue the previous item
     * @param leadingLines how many of the element's opening lines belong to that item
     * @param itemSpec per-item line counts for the items the element opens, as {@link
     *     SplitIntoListItemsFix} takes
     */
    public FoldIntoPreviousItemFix(PdfStructElem element, int leadingLines, String itemSpec) {
        this.element = element;
        this.leadingLines = leadingLines;
        this.itemSpec = itemSpec;
    }

    @Override
    public int priority() {
        return 5; // census phase: before list creation (10), LBody wraps (20), merges (30)
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        Tag enclosing = Tag.of(element).closest("L");
        if (enclosing == null) {
            throw new IllegalStateException("Element is not inside a list");
        }
        list = enclosing.elem();
        PdfStructElem opener = openerFor(element);
        if (opener == null) {
            throw new IllegalStateException("No item precedes the one this element continues");
        }
        requireSameRole(opener);

        new SplitIntoListItemsFix(element, leadingLines + "," + itemSpec).apply(ctx);
        StructTree.moveKids(element, opener);
        int pruned = StructTree.pruneEmpty(element);
        // The list was reshaped by the split; the fold only moved content between its items.
        ListItemScribble.refreshCount(list);

        logger.debug(
                "Folded {} leading line(s) of #{} into #{}'s item, pruning {} emptied element(s)",
                leadingLines,
                Tag.of(element).objNum(),
                Tag.of(opener).objNum(),
                pruned);
    }

    /**
     * Returns the content element of the item preceding the one {@code element} sits in, or null
     * where the element is not a list item's content or opens its list.
     *
     * <p>Resolved when the fix runs rather than when the issue is raised. An item ahead of this one
     * may itself lump several items, in which case it is split before this fix runs and the lines
     * being folded belong to the last of the items it becomes — an element the tree did not hold
     * when the issue was raised.
     */
    public static PdfStructElem openerFor(PdfStructElem element) {
        Tag body = Tag.of(element).parent();
        Tag item = body != null && "LBody".equals(body.role()) ? body.parent() : null;
        Tag list = item != null ? item.parent() : null;
        if (list == null || !"L".equals(list.role())) {
            return null;
        }
        List<Tag> items = itemsOf(list);
        int index = items.indexOf(item);
        return index > 0 ? contentOf(items.get(index - 1)) : null;
    }

    /**
     * Returns the element holding a list item's own content: the sole element inside its body, or
     * null where the body holds its content directly or splits it over several elements.
     */
    private static PdfStructElem contentOf(Tag item) {
        Tag body =
                item.children().stream()
                        .filter(kid -> "LBody".equals(kid.role()))
                        .findFirst()
                        .orElse(null);
        if (body == null) {
            return null;
        }
        List<IStructureNode> kids = StructTree.kidsOf(body.elem());
        return kids.size() == 1 && kids.get(0) instanceof PdfStructElem only ? only : null;
    }

    private static List<Tag> itemsOf(Tag list) {
        return list.children().stream().filter(kid -> "LI".equals(kid.role())).toList();
    }

    /**
     * Refuses an opener of another role, which would mean the element's opening lines belong to
     * something other than the paragraph they appear to continue.
     */
    private void requireSameRole(PdfStructElem opener) {
        String elementRole = Tag.of(element).role();
        String openerRole = Tag.of(opener).role();
        if (!elementRole.equals(openerRole)) {
            throw new IllegalStateException(
                    "Cannot fold a " + elementRole + " into a " + openerRole);
        }
    }

    @Override
    public String describe() {
        return "Folded continuation lines into the previous item and split the rest into items";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), list == null ? Locus.none() : Locus.atElem(ctx, list));
    }

    @Override
    public String groupLabel() {
        return "continuation lines folded into the previous list item";
    }
}
