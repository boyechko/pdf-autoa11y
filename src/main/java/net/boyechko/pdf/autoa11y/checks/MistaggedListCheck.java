// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MergeAdjacentListsFix;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoListItemsFix;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoSublistFix;
import net.boyechko.pdf.autoa11y.fixes.WrapBulletedRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects bulleted content that is not tagged as a list. A vector bullet glyph on a text line is
 * taken as the promise of one list item, so the check reads the bullets on every line of content
 * and reports where the tagging fails to deliver the items they promise.
 *
 * <p>Two defects follow from that one reading:
 *
 * <ul>
 *   <li><b>Lumped items</b> — one element's lines cover several bullets at the same indent, so it
 *       holds several items in a single tag. Invisible to structure-level analysis, since the
 *       tagging looks well-formed.
 *   <li><b>Loose items</b> — consecutive siblings each carry one bullet but no list wraps them. A
 *       run bulleted further in than the list before it is that list's sublist, and two lists left
 *       flanking such a run at one indent were one list before it came between them.
 * </ul>
 *
 * <p>Lumped items are diagnosed on leaves as the tree is left, before any container is examined,
 * and the leaf is then claimed: the container pass sees whole elements only and would misread a
 * leaf whose insides were already understood. Both passes require {@link #MIN_ITEMS_PER_LIST}
 * bullets before they will call anything a list — a lone bullet is one item, which needs no list of
 * its own.
 */
public class MistaggedListCheck extends StructTreeCheck {

    private static final Logger logger = LoggerFactory.getLogger(MistaggedListCheck.class);

    /** Bullets that must agree on an indent level before their content counts as a list. */
    private static final int MIN_ITEMS_PER_LIST = 2;

    /** Maximum y-distance (pt) between a bullet and a text line for the bullet to sit on it. */
    private static final float SAME_LINE_TOLERANCE = 3.0f;

    /** Maximum bullet x-difference (pt) for two bullets to count as the same list level. */
    private static final float SAME_LEVEL_TOLERANCE = 3.0f;

    /** Minimum bullet indent (pt) past a list's own for content to read as its sublist. */
    private static final float SUBLIST_INDENT_MIN = 10.0f;

    /** Roles that sit within a line of text rather than owning lines of their own. */
    private static final Set<String> INLINE_ROLES =
            Set.of(
                    "Link",
                    "Span",
                    "Em",
                    "Strong",
                    "Quote",
                    "Code",
                    "Reference",
                    "BibEntry",
                    "Annot");

    /** Roles whose children are examined for loose items. */
    private static final Set<String> CONTAINER_ROLES =
            Set.of("Art", "Part", "Sect", "Div", "Document");

    /**
     * Roles that never count as a loose item: containers, and anything already list- or table-like.
     */
    private static final Set<String> SKIP_ROLES =
            Set.of(
                    "Art",
                    "Part",
                    "Sect",
                    "Div",
                    "Document",
                    "L",
                    "LI",
                    "Lbl",
                    "LBody",
                    "Table",
                    "TR",
                    "TD",
                    "TH",
                    "THead",
                    "TBody",
                    "TFoot");

    private final IssueList issues = new IssueList();
    private final Set<Integer> claimed = new HashSet<>();

    @Override
    public String name() {
        return "Mistagged List Check";
    }

    @Override
    public String description() {
        return "Detects bulleted content that is not tagged as a list";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        if (ownsItsLines(ctx)) {
            detectLumpedItems(ctx);
        } else if (CONTAINER_ROLES.contains(ctx.role())) {
            detectLooseItems(ctx);
        }
    }

    /**
     * Whether the element's text lines are its own: it must be a block of its own — bullets belong
     * to the block that contains them, not to markup inside it — holding nothing but inline markup.
     * Whether an author wrapped an item's text in a Link decides nothing about which element the
     * bullets belong to, so having such children does not disqualify it.
     */
    private static boolean ownsItsLines(StructTreeContext ctx) {
        return !INLINE_ROLES.contains(ctx.role())
                && ctx.childRoles().stream().allMatch(INLINE_ROLES::contains);
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    // == Lumped items: one element covering several bullets ==============

    /**
     * Reports a leaf whose lines cover several bullets at one indent. The gaps between those
     * bullets give each item's line count, which is the spec {@link SplitIntoListItemsFix} takes.
     */
    private void detectLumpedItems(StructTreeContext ctx) {
        List<BulletLine> lines = bulletLinesOf(ctx, ctx.node());
        Float levelX =
                lines.stream()
                        .map(BulletLine::bulletX)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
        if (levelX == null) {
            return;
        }

        List<Integer> itemStarts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Float bulletX = lines.get(i).bulletX();
            if (bulletX != null && Math.abs(bulletX - levelX) <= SAME_LEVEL_TOLERANCE) {
                itemStarts.add(i);
            }
        }
        if (itemStarts.size() < MIN_ITEMS_PER_LIST) {
            return;
        }
        claimed.add(StructTree.objNum(ctx.node()));

        int leadingLines = itemStarts.get(0);
        String spec = specOf(itemStarts, lines.size());
        if (leadingLines > 0) {
            emitContinuedItem(ctx, itemStarts.size(), leadingLines, spec, levelX);
            return;
        }

        boolean splittable = ownsOnlyMarkedContent(ctx.node());
        issues.add(
                new Issue(
                        IssueType.LIST_ITEMS_LUMPED,
                        IssueSev.WARNING,
                        StructTreeCheck.locAtElem(ctx),
                        lumpedMessage(itemStarts.size(), spec, 0, splittable),
                        splittable ? new SplitIntoListItemsFix(ctx.node(), spec) : null));

        logger.debug(
                "Element #{} lumps {} bulleted items at x={}, lines {}, behind {} leading line(s),"
                        + " splittable={}",
                StructTree.objNum(ctx.node()),
                itemStarts.size(),
                String.format("%.1f", levelX),
                spec,
                leadingLines,
                splittable);
    }

    /**
     * Handles an element whose opening lines carry no bullet and so finish the item its predecessor
     * began. When that predecessor is itself a bulleted item one level out, the lines can be folded
     * back into it and the rest nested as its sublist; otherwise there is nothing to fold into and
     * the element is left for review.
     */
    private void emitContinuedItem(
            StructTreeContext ctx, int items, int leadingLines, String spec, float levelX) {
        ContinuedItem continued = continuedItem(ctx, levelX);

        // Claim the opener either way: its bullet makes it the start of an item that runs
        // on into this element, so wrapping it alone as a one-item list is wrong.
        if (continued != null) {
            claimed.add(StructTree.objNum(continued.opener()));
        }

        issues.add(
                new Issue(
                        IssueType.LIST_ITEMS_LUMPED,
                        IssueSev.WARNING,
                        StructTreeCheck.locAtElem(ctx),
                        lumpedMessage(items, spec, leadingLines, continued != null),
                        continued == null
                                ? null
                                : new SplitIntoSublistFix(
                                        ctx.node(),
                                        continued.opener(),
                                        leadingLines,
                                        spec,
                                        continued.joinInto())));

        logger.debug(
                "Element #{} lumps {} bulleted items behind {} continuation line(s) of #{}{}",
                StructTree.objNum(ctx.node()),
                items,
                leadingLines,
                continued == null ? null : StructTree.objNum(continued.opener()),
                continued == null || continued.joinInto() == null
                        ? ""
                        : ", joining list #" + StructTree.objNum(continued.joinInto()));
    }

    /**
     * The item an element continues: the sibling that opened it, and the list that item belongs to.
     * A list already tagged on the far side of the element is that list, so the rebuilt item joins
     * it rather than starting a second list beside it; a null {@code joinInto} means there is none
     * and the opener needs a list of its own.
     */
    private record ContinuedItem(PdfStructElem opener, PdfStructElem joinInto) {}

    /**
     * Resolves the item this element continues. The opener is the preceding sibling, which must
     * carry a single bullet far enough out that this element's bullets read as its sublist. Null
     * when no such sibling exists, leaving the continuation no item to rejoin.
     */
    private ContinuedItem continuedItem(StructTreeContext ctx, float levelX) {
        if (!(StructTree.parentOf(ctx.node()) instanceof PdfStructElem container)) {
            return null;
        }
        List<PdfStructElem> siblings = StructTree.childrenOf(container, PdfStructElem.class);
        int index = indexOf(siblings, ctx.node());
        if (index <= 0) {
            return null;
        }

        PdfStructElem opener = siblings.get(index - 1);
        List<Float> bulleted =
                bulletLinesOf(ctx, opener).stream()
                        .map(BulletLine::bulletX)
                        .filter(Objects::nonNull)
                        .toList();
        if (bulleted.size() != 1 || levelX - bulleted.get(0) < SUBLIST_INDENT_MIN) {
            return null;
        }
        return new ContinuedItem(
                opener, followingListAtLevel(ctx, siblings, index, bulleted.get(0)));
    }

    /**
     * Returns the list immediately after the element when its first item sits at the opener's own
     * bullet level, which makes the continued item that list's opening item rather than a list of
     * its own. Rebuilding the item inside it is what keeps one list from becoming two.
     */
    private PdfStructElem followingListAtLevel(
            StructTreeContext ctx, List<PdfStructElem> siblings, int index, float openerX) {
        if (index + 1 >= siblings.size()) {
            return null;
        }
        PdfStructElem following = siblings.get(index + 1);
        if (!"L".equals(StructTree.mappedRole(following))) {
            return null;
        }
        float listX = listItemBulletX(ctx, following, false);
        return !Float.isNaN(listX) && Math.abs(listX - openerX) <= SAME_LEVEL_TOLERANCE
                ? following
                : null;
    }

    private static int indexOf(List<PdfStructElem> elements, PdfStructElem target) {
        for (int i = 0; i < elements.size(); i++) {
            if (StructTree.isSameElement(elements.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    /** States the lumping, and where it cannot be remediated, why. */
    private static String lumpedMessage(
            int items, String spec, int leadingLines, boolean splittable) {
        String lumping = items + " bullet glyphs lumped into one element (" + spec + ")";
        if (leadingLines > 0) {
            // Lines ahead of the first bullet finish the item a previous element began, and
            // splitting on the bullets alone would hand them to the wrong item.
            String continuation =
                    lumping + ", behind " + leadingLines + " line(s) continuing the previous item";
            return splittable ? continuation : continuation + ", which no sibling opened";
        }
        return splittable ? lumping : lumping + ", interleaved with inline markup";
    }

    /**
     * Whether the element holds marked content and nothing else, which is the shape {@link
     * SplitIntoListItemsFix} can cut: items wrapped in inline markup would need their own blocks
     * split and the markup re-homed.
     */
    private static boolean ownsOnlyMarkedContent(PdfStructElem element) {
        List<IStructureNode> kids = StructTree.kidsOf(element);
        return !kids.isEmpty() && kids.stream().allMatch(kid -> kid instanceof PdfMcr);
    }

    /** Renders the per-item line counts as the comma-separated spec the split fix takes. */
    private static String specOf(List<Integer> itemStarts, int lineCount) {
        List<String> sizes = new ArrayList<>();
        for (int i = 0; i < itemStarts.size(); i++) {
            int end = i + 1 < itemStarts.size() ? itemStarts.get(i + 1) : lineCount;
            sizes.add(String.valueOf(end - itemStarts.get(i)));
        }
        return String.join(",", sizes);
    }

    // == Loose items: several siblings each covering one bullet ==========

    /**
     * One member of a run: an element holding a single bulleted item, and where that item begins. A
     * run's opening member may be an element that only ends with its item, in which case the kids
     * on that line are carved out and the rest of the element left alone.
     */
    private record Item(
            PdfStructElem element, float bulletX, List<PdfObject> carvedKids, int page) {
        boolean isWhole() {
            return carvedKids == null;
        }
    }

    /** Reports runs of consecutive children that each hold one bulleted item at the same indent. */
    private void detectLooseItems(StructTreeContext ctx) {
        List<Item> run = new ArrayList<>();
        PdfStructElem predecessor = null;

        for (int i = 0; i < ctx.children().size(); i++) {
            PdfStructElem child = ctx.children().get(i);
            String childRole = ctx.childRoles().get(i);
            Item item =
                    SKIP_ROLES.contains(childRole) || claimed.contains(StructTree.objNum(child))
                            ? null
                            : itemOf(ctx, child, run.isEmpty());

            // A run continues only while each child holds one item at the run's own indent;
            // it may span a page break, since lists do.
            if (item != null
                    && (run.isEmpty()
                            || Math.abs(item.bulletX() - run.get(0).bulletX())
                                    <= SAME_LEVEL_TOLERANCE)) {
                if (run.isEmpty()) {
                    predecessor = i > 0 ? ctx.children().get(i - 1) : null;
                }
                run.add(item);
                continue;
            }

            PdfStructElem host = emitLooseRun(ctx, run, predecessor);
            if (host != null && "L".equals(childRole)) {
                // The nested run vacates the space between the two lists —
                // if they sit at the same indent, they were one list
                emitMergeIfSameLevel(ctx, host, child);
            }
            run = new ArrayList<>();
            if (item != null) {
                predecessor = i > 0 ? ctx.children().get(i - 1) : null;
                run.add(item);
            }
        }
        emitLooseRun(ctx, run, predecessor);
    }

    /**
     * Reads an element as one list item: whole when its first line carries the only bullet, and as
     * a carve when only its last line does, which makes it the tail of a paragraph that runs into
     * the list. A carve can only open a run, since the rest of the element stays where it is.
     */
    private Item itemOf(StructTreeContext ctx, PdfStructElem element, boolean opensRun) {
        List<BulletLine> lines = bulletLinesOf(ctx, element);
        List<Integer> bulleted = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).bulletX() != null) {
                bulleted.add(i);
            }
        }
        if (bulleted.size() != 1) {
            return null;
        }

        int at = bulleted.get(0);
        BulletLine line = lines.get(at);
        if (at == 0) {
            return new Item(element, line.bulletX(), null, 0);
        }
        if (!opensRun || at != lines.size() - 1) {
            return null;
        }
        List<PdfObject> carved = kidsOnLine(ctx, element, line.bounds());
        return carved.isEmpty()
                ? null
                : new Item(
                        element, line.bulletX(), carved, StructTree.pageOf(element, ctx.docCtx()));
    }

    /** Returns the element's kids whose own content sits on the given line. */
    private List<PdfObject> kidsOnLine(
            StructTreeContext ctx, PdfStructElem element, Rectangle line) {
        List<PdfObject> onLine = new ArrayList<>();
        for (IStructureNode kid : StructTree.kidsOf(element)) {
            Rectangle bounds = boundsOfKid(ctx, kid);
            if (bounds != null && sitOnOneLine(bounds, line)) {
                onLine.add(pdfObjectOf(kid));
            }
        }
        return onLine.contains(null) ? List.of() : onLine;
    }

    /**
     * Emits a wrap fix for a run long enough to be a list and claims its elements. Returns the
     * preceding list the run will nest into as a sublist, or null when it becomes a list of its own
     * (or is too short to be one).
     */
    private PdfStructElem emitLooseRun(
            StructTreeContext ctx, List<Item> run, PdfStructElem predecessor) {
        if (run.size() < MIN_ITEMS_PER_LIST) {
            return null;
        }

        Item opener = run.get(0);
        List<PdfStructElem> whole = run.stream().filter(Item::isWhole).map(Item::element).toList();
        PdfStructElem nestInto =
                opener.isWhole() ? sublistTarget(ctx, predecessor, opener.bulletX()) : null;
        issues.add(
                new Issue(
                        nestInto != null
                                ? IssueType.SUBLIST_TAGGED_AS_PARAGRAPHS
                                : IssueType.LIST_TAGGED_AS_PARAGRAPHS,
                        IssueSev.WARNING,
                        StructTreeCheck.locAtElem(ctx),
                        run.size()
                                + " elements each carry a bullet but no "
                                + (nestInto != null ? "sublist" : "list")
                                + " wraps them",
                        opener.isWhole()
                                ? new WrapBulletedRunInList(ctx.node(), whole, nestInto)
                                : new WrapBulletedRunInList(
                                        ctx.node(),
                                        opener.element(),
                                        opener.carvedKids(),
                                        opener.page(),
                                        whole,
                                        null)));
        whole.forEach(elem -> claimed.add(StructTree.objNum(elem)));

        logger.debug(
                "Elements {} under #{} each carry one bullet, unwrapped{}{}",
                run.stream().map(item -> StructTree.objNum(item.element())).toList(),
                StructTree.objNum(ctx.node()),
                opener.isWhole()
                        ? ""
                        : " (first carved out of #" + StructTree.objNum(opener.element()) + ")",
                nestInto == null ? "" : " (sublist of #" + StructTree.objNum(nestInto) + ")");
        return nestInto;
    }

    /** Returns the preceding list an indented run should nest into as a sublist, or null. */
    private PdfStructElem sublistTarget(
            StructTreeContext ctx, PdfStructElem predecessor, float bulletX) {
        if (predecessor == null || !"L".equals(StructTree.mappedRole(predecessor))) {
            return null;
        }
        float listX = listItemBulletX(ctx, predecessor, true);
        return !Float.isNaN(listX) && bulletX - listX >= SUBLIST_INDENT_MIN ? predecessor : null;
    }

    /** Emits a merge fix when two lists flanking a nested sublist share the same indent. */
    private void emitMergeIfSameLevel(
            StructTreeContext ctx, PdfStructElem first, PdfStructElem second) {
        float firstX = listItemBulletX(ctx, first, true);
        float secondX = listItemBulletX(ctx, second, false);
        if (Float.isNaN(firstX)
                || Float.isNaN(secondX)
                || Math.abs(firstX - secondX) > SAME_LEVEL_TOLERANCE) {
            return;
        }

        issues.add(
                new Issue(
                        IssueType.LIST_SPLIT_BY_SUBLIST,
                        IssueSev.WARNING,
                        StructTreeCheck.locAtElem(ctx, second),
                        "list split in two around a sublist",
                        new MergeAdjacentListsFix(first, second)));

        logger.debug(
                "Detected split list: #{} and #{} flank a sublist at the same indent",
                StructTree.objNum(first),
                StructTree.objNum(second));
    }

    /** Returns the bullet x of a list's first or last item, or NaN when it carries none. */
    private float listItemBulletX(StructTreeContext ctx, PdfStructElem list, boolean lastItem) {
        List<PdfStructElem> items =
                StructTree.childrenOf(list, PdfStructElem.class).stream()
                        .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                        .toList();
        if (items.isEmpty()) {
            return Float.NaN;
        }
        return bulletLinesOf(ctx, items.get(lastItem ? items.size() - 1 : 0)).stream()
                .map(BulletLine::bulletX)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Float.NaN);
    }

    // == Reading bullets off the page ====================================

    /** One of an element's text lines: its bounds, and the x of its bullet, or null for none. */
    private record BulletLine(Rectangle bounds, Float bulletX) {}

    /**
     * Returns each of the element's text lines in reading order with the bullet it carries. Pages
     * are visited in order, since an element spans page breaks.
     */
    private List<BulletLine> bulletLinesOf(StructTreeContext ctx, PdfStructElem element) {
        List<BulletLine> perLine = new ArrayList<>();
        for (int pageNum : pagesTouchedBy(element)) {
            List<Content.BulletPosition> bullets =
                    ctx.docCtx()
                            .getOrComputeBulletPositions(
                                    pageNum,
                                    () ->
                                            Content.extractBulletPositionsForPage(
                                                    ctx.doc().getPage(pageNum)));
            for (Rectangle line : Content.getLineBoundsForElement(element, ctx.docCtx(), pageNum)) {
                perLine.add(new BulletLine(line, bulletOnLine(bullets, line)));
            }
        }
        return perLine;
    }

    /** Returns the x of the bullet sitting on a line, or null when the line has none. */
    private static Float bulletOnLine(List<Content.BulletPosition> bullets, Rectangle line) {
        float bottom = line.getBottom() - SAME_LINE_TOLERANCE;
        float top = line.getTop() + SAME_LINE_TOLERANCE;
        return bullets.stream()
                .filter(bullet -> bullet.y() >= bottom && bullet.y() <= top)
                .map(Content.BulletPosition::x)
                .findFirst()
                .orElse(null);
    }

    /** Whether two boxes share a text line, judged by how far their vertical centres differ. */
    private static boolean sitOnOneLine(Rectangle one, Rectangle other) {
        float centre = one.getBottom() + one.getHeight() / 2;
        float otherCentre = other.getBottom() + other.getHeight() / 2;
        return Math.abs(centre - otherCentre) <= SAME_LINE_TOLERANCE;
    }

    /** Returns a kid's own bounds on its own page, or null when it has none (e.g. an OBJR). */
    private static Rectangle boundsOfKid(StructTreeContext ctx, IStructureNode kid) {
        if (kid instanceof PdfMcr mcr) {
            int pageNum = StructTree.pageOf(mcr);
            return pageNum > 0 && mcr.getMcid() >= 0
                    ? Content.getBoundsForMcid(ctx.docCtx(), pageNum, mcr.getMcid())
                    : null;
        }
        if (kid instanceof PdfStructElem elem) {
            int pageNum = StructTree.pageOf(elem, ctx.docCtx());
            return pageNum > 0 ? Content.getBoundsForElement(elem, ctx.docCtx(), pageNum) : null;
        }
        return null;
    }

    /** Returns the underlying PdfObject for a kid, or null when it has none to move. */
    private static PdfObject pdfObjectOf(IStructureNode kid) {
        if (kid instanceof PdfStructElem elem) {
            return elem.getPdfObject();
        }
        if (kid instanceof PdfMcr mcr) {
            return mcr.getPdfObject();
        }
        return null;
    }

    /** Returns the pages an element's marked content touches, in reading order. */
    private static List<Integer> pagesTouchedBy(PdfStructElem element) {
        return StructTree.descendantsOf(element, PdfMcr.class).stream()
                .map(StructTree::pageOf)
                .filter(pageNum -> pageNum > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
