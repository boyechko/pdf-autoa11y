// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoListItemsFix;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoSublistFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bullet-census evidence for {@link MistaggedListCheck}: reads a leaf content element's lines
 * against the page's bullet glyphs. Every bullet promises a list item, so an element whose lines
 * cover several bullets at one indent level has lumped that many items into a single tag — a defect
 * invisible to structure-level analysis, since the tagging looks well-formed. The strongest
 * evidence pass: it runs on leaves before any container pass and claims what it diagnoses.
 */
final class BulletCensusDetector {

    private static final Logger logger = LoggerFactory.getLogger(BulletCensusDetector.class);

    /** Tolerance (pt) for deciding a bullet sits on a given text line. */
    private static final float SAME_LINE_TOLERANCE = 3.0f;

    /** Bullets an element must cover before its content counts as lumped items. */
    private static final int BULLET_CENSUS_MIN_ITEMS = 2;

    private final ClaimRegistry claims;
    private final IssueList issues;

    BulletCensusDetector(ClaimRegistry claims, IssueList issues) {
        this.claims = claims;
        this.issues = issues;
    }

    /**
     * Censuses one content element's lines against the page's bullet glyphs. The gaps between
     * bullets give each item's line count, which is the spec {@link SplitIntoListItemsFix} takes.
     */
    void detect(StructTreeContext ctx) {
        List<Float> lineBullets = bulletXPerLine(ctx, ctx.node());
        Float ownLevelX = lineBullets.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (ownLevelX == null) {
            return;
        }

        List<Integer> itemStarts = new ArrayList<>();
        for (int i = 0; i < lineBullets.size(); i++) {
            Float bulletX = lineBullets.get(i);
            if (bulletX != null
                    && Math.abs(bulletX - ownLevelX) <= BulletMatcher.SAME_LEVEL_TOLERANCE) {
                itemStarts.add(i);
            }
        }
        if (itemStarts.size() < BULLET_CENSUS_MIN_ITEMS) {
            return;
        }
        claims.claim(ctx.node());

        if (itemStarts.get(0) > 0) {
            emitContinuedItem(ctx, itemStarts, lineBullets.size(), ownLevelX);
            return;
        }

        String spec = specOf(itemStarts, lineBullets.size());
        issues.add(
                new Issue(
                        IssueType.LIST_ITEMS_LUMPED,
                        IssueSev.WARNING,
                        locAtNode(ctx),
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
            claims.claim(continued.opener());
        }

        issues.add(
                new Issue(
                        IssueType.LIST_ITEMS_LUMPED,
                        IssueSev.WARNING,
                        locAtNode(ctx),
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
        if (bulleted.size() != 1
                || ownLevelX - bulleted.get(0) < BulletMatcher.SUBLIST_INDENT_MIN) {
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
        float listX = BulletMatcher.listItemBulletX(ctx, following, false);
        if (Float.isNaN(listX) || Math.abs(listX - openerX) > BulletMatcher.SAME_LEVEL_TOLERANCE) {
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
            List<Content.BulletPosition> bullets = BulletMatcher.bulletsFor(ctx, pageNum);
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

    private static IssueLoc locAtNode(StructTreeContext ctx) {
        return IssueLoc.atElem(ctx.node(), ctx.getPageNumber(), ctx.role(), ctx.path());
    }
}
