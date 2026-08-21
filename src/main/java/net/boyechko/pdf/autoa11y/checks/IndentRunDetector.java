// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.WrapParagraphRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indentation evidence for {@link MistaggedListCheck}: runs of 3+ consecutive P siblings sharing a
 * left edge indented past their non-run siblings read as a list. Weaker than bullet evidence — the
 * bullet passes claim their elements first, and this pass only considers what is left unclaimed.
 */
final class IndentRunDetector {

    private static final Logger logger = LoggerFactory.getLogger(IndentRunDetector.class);

    private static final int INDENT_MIN_RUN_LENGTH = 3;
    private static final float LEFT_EDGE_TOLERANCE = 2.0f;
    private static final float INDENT_THRESHOLD = 10.0f;

    private final ClaimRegistry claims;
    private final IssueList issues;

    IndentRunDetector(ClaimRegistry claims, IssueList issues) {
        this.claims = claims;
        this.issues = issues;
    }

    /** Detects indented paragraph runs among a container's unclaimed children. */
    void detect(StructTreeContext ctx) {
        for (List<Integer> runIndices : findUnclaimedParagraphRuns(ctx)) {
            checkRunForListFeatures(ctx, runIndices);
        }
    }

    /** Finds maximal runs of 3+ consecutive unclaimed "P" children. */
    private List<List<Integer>> findUnclaimedParagraphRuns(StructTreeContext ctx) {
        List<List<Integer>> runs = new ArrayList<>();
        List<Integer> currentRun = new ArrayList<>();

        for (int i = 0; i < ctx.childRoles().size(); i++) {
            if ("P".equals(ctx.childRoles().get(i)) && !claims.isClaimed(ctx.children().get(i))) {
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
                        IssueLoc.atElem(ctx.node(), ctx.getPageNumber(), ctx.role(), ctx.path()),
                        subRun.elements.size()
                                + " consecutive P elements appear to be a list (indented "
                                + String.format("%.0f", indent)
                                + "pt)",
                        fix);
        issues.add(issue);
        subRun.elements.forEach(claims::claim);

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
}
