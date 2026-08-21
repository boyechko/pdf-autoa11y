// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MergeAdjacentListsFix;
import net.boyechko.pdf.autoa11y.fixes.ParagraphOfLinksFix;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoListItemsFix;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoSublistFix;
import net.boyechko.pdf.autoa11y.fixes.WrapBulletAlignedKidsInLBody;
import net.boyechko.pdf.autoa11y.fixes.WrapParagraphRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects content that reads as a list but is not tagged as one, using three kinds of evidence in
 * order of strength:
 *
 * <ol>
 *   <li><b>Bullet glyphs</b>: vector bullet circles in the content stream matched to elements by
 *       y-position. Runs become lists; indented runs become sublists nested in the preceding list;
 *       lists split around a sublist are merged; tall elements are drilled into for bullet-aligned
 *       raw kids.
 *   <li><b>Indentation</b>: runs of 3+ consecutive P siblings sharing a left edge indented past
 *       their non-run siblings.
 *   <li><b>Link-only paragraphs</b>: elements whose children are all Links, excluding those whose
 *       Links all target one destination, which are single links split across lines.
 * </ol>
 *
 * <p>Each element is claimed by the strongest evidence that matches it, so the strategies never
 * emit competing fixes for the same content.
 *
 * <p>Ahead of those passes, every leaf content element gets a <b>bullet census</b>, which reads the
 * page a line at a time rather than an element at a time. A bullet glyph promises a list item, so
 * an element whose lines cover several bullets has lumped that many items into one tag — whether or
 * not it already sits in an {@code L}, and invisibly to any pass that compares whole elements. The
 * gaps between bullets give each item's line count, which drives {@link SplitIntoListItemsFix}; a
 * census-claimed element is then left alone by the passes above.
 *
 * @see SplitIntoListItemsFix
 * @see WrapParagraphRunInList
 * @see WrapBulletAlignedKidsInLBody
 * @see MergeAdjacentListsFix
 * @see ParagraphOfLinksFix
 */
public class MistaggedListCheck extends StructTreeCheck {

    private static final Logger logger = LoggerFactory.getLogger(MistaggedListCheck.class);

    private static final Set<String> CONTAINER_ROLES =
            Set.of("Art", "Part", "Sect", "Div", "Document");
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

    // -- Bullet evidence --
    private static final int BULLET_MIN_RUN_LENGTH = 1;
    private static final float Y_OVERLAP_TOLERANCE = 3.0f;

    /** Maximum element height to match — roughly two lines of text. */
    private static final float MAX_ELEMENT_HEIGHT = 30.0f;

    /** Minimum bullet indent (pt) for a run to count as a sublist of the preceding list. */
    private static final float SUBLIST_INDENT_MIN = 10.0f;

    /** Maximum bullet x-difference (pt) for bullets to count as the same list level. */
    private static final float SAME_LEVEL_TOLERANCE = 3.0f;

    // -- Bullet census --
    /** Tolerance (pt) for deciding a bullet sits on a given text line. */
    private static final float SAME_LINE_TOLERANCE = 3.0f;

    /** Bullets an element must cover before its content counts as lumped items. */
    private static final int BULLET_CENSUS_MIN_ITEMS = 2;

    private final IssueList issues = new IssueList();
    private final ClaimRegistry claims = new ClaimRegistry();
    private final LinkParagraphDetector linkParagraphs = new LinkParagraphDetector(claims, issues);
    private final IndentRunDetector indentRuns = new IndentRunDetector(claims, issues);

    @Override
    public String name() {
        return "Mistagged List Check";
    }

    @Override
    public String description() {
        return "Detects bulleted, indented, or link-only content that should be lists";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        linkParagraphs.collect(ctx);
        return true;
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        // Leaves run first: a leaf claimed as lumped is then off-limits to the passes
        // its container runs, which see only whole elements and would misread it.
        if (ctx.children().isEmpty()) {
            censusElementBullets(ctx);
            return;
        }
        if (!CONTAINER_ROLES.contains(ctx.role())) {
            return;
        }

        findBulletMatchedRuns(ctx);
        indentRuns.detect(ctx);
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        linkParagraphs.emitUnclaimed();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void claim(PdfStructElem elem) {
        claims.claim(elem);
    }

    private boolean isClaimed(PdfStructElem elem) {
        return claims.isClaimed(elem);
    }

    // == Bullet census evidence ==========================================

    /**
     * Censuses one content element's lines against the page's bullet glyphs. Every bullet promises
     * a list item, so an element whose lines cover several bullets at one indent level has lumped
     * that many items into a single tag — a defect invisible to structure-level analysis, since the
     * tagging looks well-formed. The gaps between bullets give each item's line count, which is the
     * spec {@link SplitIntoListItemsFix} takes.
     */
    private void censusElementBullets(StructTreeContext ctx) {
        List<Float> lineBullets = bulletXPerLine(ctx, ctx.node());
        Float ownLevelX = lineBullets.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (ownLevelX == null) {
            return;
        }

        List<Integer> itemStarts = new ArrayList<>();
        for (int i = 0; i < lineBullets.size(); i++) {
            Float bulletX = lineBullets.get(i);
            if (bulletX != null && Math.abs(bulletX - ownLevelX) <= SAME_LEVEL_TOLERANCE) {
                itemStarts.add(i);
            }
        }
        if (itemStarts.size() < BULLET_CENSUS_MIN_ITEMS) {
            return;
        }
        claim(ctx.node());

        if (itemStarts.get(0) > 0) {
            emitContinuedItem(ctx, itemStarts, lineBullets.size(), ownLevelX);
            return;
        }

        String spec = specOf(itemStarts, lineBullets.size());
        issues.add(
                new Issue(
                        IssueType.LIST_ITEMS_LUMPED,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        itemStarts.size() + " bullet glyphs lumped into one element (" + spec + ")",
                        new SplitIntoListItemsFix(ctx.node(), spec)));

        logger.debug(
                "Element #{} lumps {} bulleted items at x={}, lines {}",
                StructTree.objNum(ctx.node()),
                itemStarts.size(),
                String.format("%.1f", ownLevelX),
                spec);
    }

    /**
     * Handles an element whose opening lines carry no bullet and so finish the item its predecessor
     * began. When that predecessor is itself a bulleted item one level out, the lines can be folded
     * back into it and the rest nested as its sublist; otherwise there is nothing to fold into and
     * the element is left for review.
     */
    private void emitContinuedItem(
            StructTreeContext ctx, List<Integer> itemStarts, int lineCount, float ownLevelX) {
        int leadingLines = itemStarts.get(0);
        ContinuedItem continued = continuedItem(ctx, ownLevelX);
        String spec = specOf(itemStarts, lineCount);

        // Claim the opener either way: its bullet makes it the start of an item that runs
        // on into this element, so wrapping it alone as a one-item list is wrong.
        if (continued != null) {
            claim(continued.opener());
        }

        issues.add(
                new Issue(
                        IssueType.LIST_ITEMS_LUMPED,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        itemStarts.size()
                                + " bullet glyphs in one element, behind "
                                + leadingLines
                                + " line(s) continuing the previous item"
                                + (continued == null ? "" : " (" + spec + ")"),
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
                itemStarts.size(),
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
     * Resolves the item this element continues. The opener is the preceding sibling, which must be
     * a leaf carrying a single bullet far enough out that this element's bullets read as its
     * sublist. Null when no such sibling exists, leaving the continuation no item to rejoin.
     */
    private ContinuedItem continuedItem(StructTreeContext ctx, float ownLevelX) {
        if (!(StructTree.parentOf(ctx.node()) instanceof PdfStructElem container)) {
            return null;
        }
        List<PdfStructElem> siblings = StructTree.childrenOf(container, PdfStructElem.class);
        int index = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (StructTree.isSameElement(siblings.get(i), ctx.node())) {
                index = i;
                break;
            }
        }
        if (index <= 0) {
            return null;
        }

        PdfStructElem opener = siblings.get(index - 1);
        if (!StructTree.childrenOf(opener, PdfStructElem.class).isEmpty()) {
            return null;
        }
        List<Float> bulleted =
                bulletXPerLine(ctx, opener).stream().filter(Objects::nonNull).toList();
        if (bulleted.size() != 1 || ownLevelX - bulleted.get(0) < SUBLIST_INDENT_MIN) {
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
        if (Float.isNaN(listX) || Math.abs(listX - openerX) > SAME_LEVEL_TOLERANCE) {
            return null;
        }
        return following;
    }

    /**
     * Returns the x of the bullet on each of the element's lines in reading order, or null for a
     * line that carries none. Pages are visited in order, since a lumped element spans page breaks.
     */
    private List<Float> bulletXPerLine(StructTreeContext ctx, PdfStructElem element) {
        List<Float> perLine = new ArrayList<>();
        for (int pageNum : pagesTouchedBy(element)) {
            List<Content.BulletPosition> bullets = bulletsFor(ctx, pageNum);
            for (Rectangle line : Content.getLineBoundsForElement(element, ctx.docCtx(), pageNum)) {
                perLine.add(bulletOnLine(bullets, line));
            }
        }
        return perLine;
    }

    /** Returns the x of the bullet sitting on a line, or null when the line has none. */
    private static Float bulletOnLine(List<Content.BulletPosition> bullets, Rectangle line) {
        float bottom = line.getBottom() - SAME_LINE_TOLERANCE;
        float top = line.getTop() + SAME_LINE_TOLERANCE;
        return bullets.stream()
                .filter(b -> b.y() >= bottom && b.y() <= top)
                .map(Content.BulletPosition::x)
                .findFirst()
                .orElse(null);
    }

    /** Returns the pages an element's marked content touches, in reading order. */
    private static List<Integer> pagesTouchedBy(PdfStructElem element) {
        return StructTree.descendantsOf(element, PdfMcr.class).stream()
                .map(StructTree::pageOf)
                .filter(pageNum -> pageNum > 0)
                .distinct()
                .sorted()
                .toList();
    }

    /** Renders the per-item line counts as the comma-separated spec the split fixes take. */
    private static String specOf(List<Integer> itemStarts, int lineCount) {
        return itemLineCounts(itemStarts, lineCount).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /** Returns each item's line count: from its own bullet's line up to the next item's. */
    private static List<Integer> itemLineCounts(List<Integer> itemStarts, int lineCount) {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < itemStarts.size(); i++) {
            int end = i + 1 < itemStarts.size() ? itemStarts.get(i + 1) : lineCount;
            sizes.add(end - itemStarts.get(i));
        }
        return sizes;
    }

    // == Bullet evidence =================================================

    private void findBulletMatchedRuns(StructTreeContext ctx) {
        List<PdfStructElem> currentRun = new ArrayList<>();
        PdfStructElem runPredecessor = null;
        float runBulletX = Float.NaN;
        boolean runUniform = true;

        for (int i = 0; i < ctx.children().size(); i++) {
            PdfStructElem child = ctx.children().get(i);
            String childRole = ctx.childRoles().get(i);

            // Skip containers, lists, tables — only match leaf-like content elements.
            // A claimed child is spoken for by the census, which saw inside it.
            if (SKIP_ROLES.contains(childRole) || isClaimed(child)) {
                PdfStructElem host =
                        emitRun(ctx, currentRun, runPredecessor, runBulletX, runUniform);
                if (host != null && "L".equals(childRole)) {
                    // The nested run vacates the space between the two lists —
                    // if they sit at the same indent, they were one list
                    emitMergeIfSameLevel(ctx, host, child);
                }
                currentRun = new ArrayList<>();
                runBulletX = Float.NaN;
                runUniform = true;
                continue;
            }

            // Each element is judged against its own page's bullets and bounds;
            // a run may span a page break, since lists do
            int pageNum = StructTree.pageOf(child, ctx.docCtx());
            Rectangle bounds =
                    pageNum > 0 ? Content.getBoundsForElement(child, ctx.docCtx(), pageNum) : null;
            Content.BulletPosition bullet =
                    bounds != null && bounds.getHeight() <= MAX_ELEMENT_HEIGHT
                            ? findMatchingBullet(bulletsFor(ctx, pageNum), bounds)
                            : null;
            if (bullet != null) {
                if (currentRun.isEmpty()) {
                    runPredecessor = i > 0 ? ctx.children().get(i - 1) : null;
                    runBulletX = bullet.x();
                } else if (Math.abs(bullet.x() - runBulletX) > SAME_LEVEL_TOLERANCE) {
                    runUniform = false;
                }
                currentRun.add(child);
            } else {
                emitRun(ctx, currentRun, runPredecessor, runBulletX, runUniform);
                currentRun = new ArrayList<>();
                runBulletX = Float.NaN;
                runUniform = true;

                // Drill into too-tall elements to find bullet-aligned raw kids
                if (bounds != null
                        && bounds.getHeight() > MAX_ELEMENT_HEIGHT
                        && !isClaimed(child)) {
                    findBulletAlignedKidsInElement(ctx, child);
                }
            }
        }
        emitRun(ctx, currentRun, runPredecessor, runBulletX, runUniform);
    }

    /** Returns the (cached) bullet glyph positions for a page. */
    private List<Content.BulletPosition> bulletsFor(StructTreeContext ctx, int pageNum) {
        return ctx.docCtx()
                .getOrComputeBulletPositions(
                        pageNum,
                        () -> Content.extractBulletPositionsForPage(ctx.doc().getPage(pageNum)));
    }

    /**
     * Emits a wrap fix for a run. Returns the preceding list the run will nest into as a sublist,
     * or null when the run is wrapped as a plain sibling list (or is too short).
     */
    private PdfStructElem emitRun(
            StructTreeContext ctx,
            List<PdfStructElem> run,
            PdfStructElem predecessor,
            float bulletX,
            boolean uniform) {
        if (run.size() < BULLET_MIN_RUN_LENGTH) {
            return null;
        }

        PdfStructElem nestTarget = sublistTarget(ctx, predecessor, bulletX, uniform);
        IssueFix fix = new WrapParagraphRunInList(ctx.node(), run, nestTarget);
        Issue issue =
                new Issue(
                        nestTarget != null
                                ? IssueType.SUBLIST_TAGGED_AS_PARAGRAPHS
                                : IssueType.LIST_TAGGED_AS_PARAGRAPHS,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        run.size()
                                + " elements appear to be a "
                                + (nestTarget != null ? "sublist" : "list"),
                        fix);
        issues.add(issue);
        run.forEach(this::claim);

        logger.debug(
                "Detected {} elements with bullet glyphs under obj. #{} on page {}{}",
                run.size(),
                StructTree.objNum(ctx.node()),
                StructTree.pageOf(run.get(0), ctx.docCtx()),
                nestTarget != null ? " (sublist of #" + StructTree.objNum(nestTarget) + ")" : "");
        return nestTarget;
    }

    /** Returns the preceding list an indented, uniform run should nest into, or null. */
    private PdfStructElem sublistTarget(
            StructTreeContext ctx, PdfStructElem predecessor, float bulletX, boolean uniform) {
        if (!uniform || predecessor == null || Float.isNaN(bulletX)) {
            return null;
        }
        if (!"L".equals(StructTree.mappedRole(predecessor))) {
            return null;
        }
        float listX = listItemBulletX(ctx, predecessor, true);
        if (Float.isNaN(listX) || bulletX - listX < SUBLIST_INDENT_MIN) {
            return null;
        }
        return predecessor;
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

        Issue issue =
                new Issue(
                        IssueType.LIST_SPLIT_BY_SUBLIST,
                        IssueSev.WARNING,
                        locAtElem(ctx, second),
                        "list split in two around a sublist",
                        new MergeAdjacentListsFix(first, second));
        issues.add(issue);

        logger.debug(
                "Detected split list: #{} and #{} flank a sublist at the same indent",
                StructTree.objNum(first),
                StructTree.objNum(second));
    }

    /** Returns the bullet x-position matched to a list's first or last item, or NaN. */
    private float listItemBulletX(StructTreeContext ctx, PdfStructElem list, boolean lastItem) {
        List<PdfStructElem> items =
                StructTree.childrenOf(list, PdfStructElem.class).stream()
                        .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                        .toList();
        if (items.isEmpty()) {
            return Float.NaN;
        }
        PdfStructElem li = items.get(lastItem ? items.size() - 1 : 0);
        int pageNum = StructTree.pageOf(li, ctx.docCtx());
        if (pageNum <= 0) {
            return Float.NaN;
        }
        Rectangle bounds = Content.getBoundsForElement(li, ctx.docCtx(), pageNum);
        if (bounds == null) {
            return Float.NaN;
        }
        Content.BulletPosition bullet = findMatchingBullet(bulletsFor(ctx, pageNum), bounds);
        return bullet != null ? bullet.x() : Float.NaN;
    }

    /** A group of consecutive raw kids that align with the same bullet y-position. */
    private record BulletAlignedGroup(List<PdfObject> kidObjects, float bulletY, int pageNum) {}

    /**
     * Drills into an element's raw kids to find groups that align with bullet positions. Each group
     * becomes a WrapBulletAlignedKidsInLBody fix.
     */
    private void findBulletAlignedKidsInElement(StructTreeContext ctx, PdfStructElem element) {
        List<IStructureNode> rawKids = element.getKids();
        if (rawKids == null || rawKids.isEmpty()) {
            return;
        }

        List<BulletAlignedGroup> groups = new ArrayList<>();
        List<PdfObject> currentGroup = new ArrayList<>();
        float currentBulletY = Float.NaN;
        int currentPage = 0;

        for (IStructureNode kid : rawKids) {
            // Each kid is judged against its own page — the element may span pages
            int kidPage = pageOfRawKid(kid, ctx);
            Rectangle kidBounds = kidPage > 0 ? boundsForRawKid(kid, ctx, kidPage) : null;

            if (kidBounds == null) {
                // Skip kids without bounds (e.g., OBJRs)
                continue;
            }

            Content.BulletPosition matchedBullet =
                    findMatchingBullet(bulletsFor(ctx, kidPage), kidBounds);
            if (matchedBullet != null) {
                boolean sameBullet =
                        kidPage == currentPage
                                && Math.abs(currentBulletY - matchedBullet.y())
                                        < Y_OVERLAP_TOLERANCE;
                if (!currentGroup.isEmpty() && !sameBullet) {
                    // Different bullet — flush current group and start new one
                    groups.add(
                            new BulletAlignedGroup(
                                    new ArrayList<>(currentGroup), currentBulletY, currentPage));
                    currentGroup.clear();
                }
                currentGroup.add(pdfObjectOf(kid));
                currentBulletY = matchedBullet.y();
                currentPage = kidPage;
            } else {
                if (!currentGroup.isEmpty()) {
                    groups.add(
                            new BulletAlignedGroup(
                                    new ArrayList<>(currentGroup), currentBulletY, currentPage));
                    currentGroup.clear();
                    currentBulletY = Float.NaN;
                    currentPage = 0;
                }
            }
        }

        // Flush final group
        if (!currentGroup.isEmpty()) {
            groups.add(
                    new BulletAlignedGroup(
                            new ArrayList<>(currentGroup), currentBulletY, currentPage));
        }

        if (!groups.isEmpty()) {
            claim(element);
        }

        // Emit issues for each group
        for (BulletAlignedGroup group : groups) {
            IssueFix fix =
                    new WrapBulletAlignedKidsInLBody(
                            element, group.kidObjects(), group.bulletY(), group.pageNum());
            Issue issue =
                    new Issue(
                            IssueType.BULLET_ALIGNED_KIDS_IN_ELEMENT,
                            IssueSev.WARNING,
                            locAtElem(ctx, element),
                            group.kidObjects().size()
                                    + " raw kids aligned with bullet glyph inside "
                                    + element.getRole().getValue(),
                            fix);
            issues.add(issue);

            logger.debug(
                    "Found {} bullet-aligned raw kids in obj. #{} (bulletY={})",
                    group.kidObjects().size(),
                    StructTree.objNum(element),
                    String.format("%.1f", group.bulletY()));
        }
    }

    // == Shared helpers ==================================================

    /** Returns the underlying PdfObject for a raw kid (struct element, MCR, or OBJR). */
    private static PdfObject pdfObjectOf(IStructureNode kid) {
        if (kid instanceof PdfStructElem elem) {
            return elem.getPdfObject();
        }
        if (kid instanceof PdfMcr mcr) {
            return mcr.getPdfObject();
        }
        return null;
    }

    /** Returns the page a raw kid's content lives on, or 0 if it cannot be determined. */
    private int pageOfRawKid(IStructureNode kid, StructTreeContext ctx) {
        if (kid instanceof PdfObjRef) {
            return 0;
        } else if (kid instanceof PdfMcr mcr) {
            return StructTree.pageOf(mcr);
        } else if (kid instanceof PdfStructElem structKid) {
            return StructTree.pageOf(structKid, ctx.docCtx());
        }
        return 0;
    }

    /** Computes bounds for a single raw kid (MCR or struct element). */
    private Rectangle boundsForRawKid(IStructureNode kid, StructTreeContext ctx, int pageNum) {
        if (kid instanceof PdfObjRef) {
            return null;
        } else if (kid instanceof PdfMcr mcr) {
            int mcid = mcr.getMcid();
            if (mcid < 0) return null;
            return Content.getBoundsForMcid(ctx.docCtx(), pageNum, mcid);
        } else if (kid instanceof PdfStructElem structKid) {
            return Content.getBoundsForElement(structKid, ctx.docCtx(), pageNum);
        }
        return null;
    }

    /** Finds the bullet that matches a given bounding box, or null if none matches. */
    private Content.BulletPosition findMatchingBullet(
            List<Content.BulletPosition> bullets, Rectangle bounds) {
        float bottom = bounds.getBottom() - Y_OVERLAP_TOLERANCE;
        float top = bounds.getTop() + Y_OVERLAP_TOLERANCE;
        return bullets.stream()
                .filter(b -> b.y() >= bottom && b.y() <= top)
                .findFirst()
                .orElse(null);
    }
}
