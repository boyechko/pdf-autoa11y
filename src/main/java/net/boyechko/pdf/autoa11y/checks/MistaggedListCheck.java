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
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MergeAdjacentListsFix;
import net.boyechko.pdf.autoa11y.fixes.ParagraphOfLinksFix;
import net.boyechko.pdf.autoa11y.fixes.WrapBulletAlignedKidsInLBody;
import net.boyechko.pdf.autoa11y.fixes.WrapParagraphRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
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
 *   <li><b>Link-only paragraphs</b>: elements whose children are all Links.
 * </ol>
 *
 * <p>Each element is claimed by the strongest evidence that matches it, so the strategies never
 * emit competing fixes for the same content.
 *
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

    // -- Indent evidence --
    private static final int INDENT_MIN_RUN_LENGTH = 3;
    private static final float LEFT_EDGE_TOLERANCE = 2.0f;
    private static final float INDENT_THRESHOLD = 10.0f;

    // -- Link evidence --
    private static final int MIN_LINKS_COUNT = 2;

    private final IssueList issues = new IssueList();

    /** Object numbers of elements already claimed by a stronger evidence pass. */
    private final Set<Integer> claimed = new HashSet<>();

    /** Link-only paragraphs collected during traversal, reconciled in afterTraversal. */
    private final List<LinkParagraphCandidate> linkParagraphs = new ArrayList<>();

    private record LinkParagraphCandidate(
            PdfStructElem node, List<PdfStructElem> children, IssueLoc loc) {}

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
        collectLinkParagraphCandidate(ctx);
        return true;
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
        findIndentedRuns(ctx);
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        emitUnclaimedLinkParagraphs();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void claim(PdfStructElem elem) {
        claimed.add(StructTree.objNum(elem));
    }

    private boolean isClaimed(PdfStructElem elem) {
        return claimed.contains(StructTree.objNum(elem));
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

    // == Indent evidence =================================================

    private void findIndentedRuns(StructTreeContext ctx) {
        for (List<Integer> runIndices : findUnclaimedParagraphRuns(ctx)) {
            checkRunForListFeatures(ctx, runIndices);
        }
    }

    /** Finds maximal runs of 3+ consecutive unclaimed "P" children. */
    private List<List<Integer>> findUnclaimedParagraphRuns(StructTreeContext ctx) {
        List<List<Integer>> runs = new ArrayList<>();
        List<Integer> currentRun = new ArrayList<>();

        for (int i = 0; i < ctx.childRoles().size(); i++) {
            if ("P".equals(ctx.childRoles().get(i)) && !isClaimed(ctx.children().get(i))) {
                currentRun.add(i);
            } else {
                if (currentRun.size() >= INDENT_MIN_RUN_LENGTH) {
                    runs.add(currentRun);
                }
                currentRun = new ArrayList<>();
            }
        }
        if (currentRun.size() >= INDENT_MIN_RUN_LENGTH) {
            runs.add(currentRun);
        }

        return runs;
    }

    /**
     * Validates a candidate run using spatial analysis. If left edges are inconsistent across the
     * whole run (e.g., the last few elements aren't indented), splits it into contiguous sub-runs
     * of elements sharing the same left edge and checks each sub-run independently.
     */
    private void checkRunForListFeatures(StructTreeContext ctx, List<Integer> runIndices) {
        List<Float> leftEdges = new ArrayList<>();
        List<PdfStructElem> elements = new ArrayList<>();
        List<Integer> validIndices = new ArrayList<>();

        for (int idx : runIndices) {
            PdfStructElem p = ctx.children().get(idx);
            int pageNum = StructTree.pageOf(p, ctx.docCtx());
            Rectangle bounds =
                    pageNum > 0 ? Content.getBoundsForElement(p, ctx.docCtx(), pageNum) : null;
            if (bounds != null) {
                leftEdges.add(bounds.getLeft());
                elements.add(p);
                validIndices.add(idx);
            }
        }

        if (elements.size() < INDENT_MIN_RUN_LENGTH) {
            return;
        }

        for (SubRun subRun : splitByLeftEdge(leftEdges, elements, validIndices)) {
            checkSubRunForListFeatures(ctx, subRun);
        }
    }

    /** Splits elements into contiguous sub-runs where left edges match within tolerance. */
    private List<SubRun> splitByLeftEdge(
            List<Float> leftEdges, List<PdfStructElem> elements, List<Integer> indices) {
        List<SubRun> subRuns = new ArrayList<>();
        int start = 0;

        while (start < leftEdges.size()) {
            float anchor = leftEdges.get(start);
            int end = start + 1;

            while (end < leftEdges.size()
                    && Math.abs(leftEdges.get(end) - anchor) <= LEFT_EDGE_TOLERANCE) {
                end++;
            }

            int length = end - start;
            if (length >= INDENT_MIN_RUN_LENGTH) {
                subRuns.add(
                        new SubRun(
                                elements.subList(start, end),
                                indices.subList(start, end),
                                leftEdges.subList(start, end)));
            }

            start = end;
        }

        return subRuns;
    }

    /** Checks a sub-run against reference siblings for indentation and creates an issue. */
    private void checkSubRunForListFeatures(StructTreeContext ctx, SubRun subRun) {
        float runMedianLeft = median(subRun.leftEdges);
        float referenceLeft = getReferenceLeftEdge(ctx, subRun.indices);

        if (referenceLeft < 0) {
            logger.debug(
                    "No reference left edge for P sub-run under obj. #{}, skipping",
                    StructTree.objNum(ctx.node()));
            return;
        }

        float indent = runMedianLeft - referenceLeft;
        if (indent < INDENT_THRESHOLD) {
            logger.debug(
                    "P sub-run under obj. #{} indent {}pt < threshold {}pt, skipping",
                    StructTree.objNum(ctx.node()),
                    String.format("%.1f", indent),
                    INDENT_THRESHOLD);
            return;
        }

        IssueFix fix = new WrapParagraphRunInList(ctx.node(), subRun.elements);
        Issue issue =
                new Issue(
                        IssueType.LIST_TAGGED_AS_PARAGRAPHS,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        subRun.elements.size()
                                + " consecutive P elements appear to be a list (indented "
                                + String.format("%.0f", indent)
                                + "pt)",
                        fix);
        issues.add(issue);
        subRun.elements.forEach(this::claim);

        logger.debug(
                "Detected suspected list of {} elements under obj. #{} (indent {}pt)",
                subRun.elements.size(),
                StructTree.objNum(ctx.node()),
                String.format("%.1f", indent));
    }

    private record SubRun(
            List<PdfStructElem> elements, List<Integer> indices, List<Float> leftEdges) {}

    /**
     * Gets the minimum left edge from non-run siblings (H1, H2, other P elements, etc.) to use as a
     * reference for indentation comparison.
     */
    private float getReferenceLeftEdge(StructTreeContext ctx, List<Integer> runIndices) {
        Set<Integer> runIndexSet = Set.copyOf(runIndices);
        float minLeft = -1;

        for (int i = 0; i < ctx.children().size(); i++) {
            if (runIndexSet.contains(i)) {
                continue;
            }

            PdfStructElem sibling = ctx.children().get(i);
            int pageNum = StructTree.pageOf(sibling, ctx.docCtx());
            Rectangle bounds =
                    pageNum > 0
                            ? Content.getBoundsForElement(sibling, ctx.docCtx(), pageNum)
                            : null;
            if (bounds != null && bounds.getWidth() > 0) {
                float left = bounds.getLeft();
                if (minLeft < 0 || left < minLeft) {
                    minLeft = left;
                }
            }
        }

        return minLeft;
    }

    private float median(List<Float> values) {
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(Float::compare);
        int n = sorted.size();
        if (n % 2 == 0) {
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0f;
        }
        return sorted.get(n / 2);
    }

    // == Link evidence ===================================================

    /** Collects elements whose children are all Links; emitted later unless claimed. */
    private void collectLinkParagraphCandidate(StructTreeContext ctx) {
        if (ctx.children().size() < MIN_LINKS_COUNT) {
            return;
        }

        // Skip if the element has non-struct-elem kids (MCRs/OBJRs) that would
        // be orphaned when we convert Link children to LI > LBody > Link.
        var allKids = ctx.node().getKids();
        if (allKids != null && allKids.size() != ctx.children().size()) {
            return;
        }

        if (ctx.children().stream().allMatch(c -> c.getRole().equals(PdfName.Link))) {
            linkParagraphs.add(
                    new LinkParagraphCandidate(ctx.node(), ctx.children(), locAtElem(ctx)));
        }
    }

    /** Emits link-paragraph issues for candidates no stronger evidence pass claimed. */
    private void emitUnclaimedLinkParagraphs() {
        for (LinkParagraphCandidate candidate : linkParagraphs) {
            if (isClaimed(candidate.node())) {
                continue;
            }

            IssueFix fix = new ParagraphOfLinksFix(candidate.node(), candidate.children());
            Issue issue =
                    new Issue(
                            IssueType.PARAGRAPH_OF_LINKS,
                            IssueSev.ERROR,
                            candidate.loc(),
                            "Paragraph contains only links",
                            fix);
            issues.add(issue);
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
