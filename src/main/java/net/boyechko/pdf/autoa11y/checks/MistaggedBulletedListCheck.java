/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MergeAdjacentListsFix;
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
 * Detects vector bullet glyphs in the content stream and matches them to tagged structure elements
 * by y-position overlap. Elements aligned with bullets are wrapped in L > LI > LBody structure.
 */
public class MistaggedBulletedListCheck extends StructTreeCheck {

    private static final Logger logger = LoggerFactory.getLogger(MistaggedBulletedListCheck.class);

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
    private static final int MIN_RUN_LENGTH = 1;
    private static final float Y_OVERLAP_TOLERANCE = 3.0f;

    /** Maximum element height to match — roughly two lines of text. */
    private static final float MAX_ELEMENT_HEIGHT = 30.0f;

    /** Minimum bullet indent (pt) for a run to count as a sublist of the preceding list. */
    private static final float SUBLIST_INDENT_MIN = 10.0f;

    /** Maximum bullet x-difference (pt) for bullets to count as the same list level. */
    private static final float SAME_LEVEL_TOLERANCE = 3.0f;

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Mistagged Bulleted List Check";
    }

    @Override
    public String description() {
        return "Detects vector bullet glyphs near elements that should be lists";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        if (!CONTAINER_ROLES.contains(ctx.role())) {
            return;
        }
        if (ctx.children().isEmpty()) {
            return;
        }

        findBulletMatchedRuns(ctx);
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void findBulletMatchedRuns(StructTreeContext ctx) {
        List<PdfStructElem> currentRun = new ArrayList<>();
        PdfStructElem runPredecessor = null;
        float runBulletX = Float.NaN;
        boolean runUniform = true;

        for (int i = 0; i < ctx.children().size(); i++) {
            PdfStructElem child = ctx.children().get(i);
            String childRole = ctx.childRoles().get(i);

            // Skip containers, lists, tables — only match leaf-like content elements
            if (SKIP_ROLES.contains(childRole)) {
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
                if (bounds != null && bounds.getHeight() > MAX_ELEMENT_HEIGHT) {
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
        if (run.size() < MIN_RUN_LENGTH) {
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
