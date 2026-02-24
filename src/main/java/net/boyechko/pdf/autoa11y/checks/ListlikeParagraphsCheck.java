/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
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
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.fixes.children.WrapParagraphRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeChecker;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects runs of consecutive P elements that are likely list items. Uses structural pattern
 * detection (3+ consecutive P siblings) combined with spatial analysis (consistent left-edge
 * indentation relative to non-run siblings) to identify paragraphs that should be wrapped in list
 * structure.
 */
public class ListlikeParagraphsCheck implements StructTreeChecker {

    private static final Logger logger = LoggerFactory.getLogger(ListlikeParagraphsCheck.class);

    private static final Set<String> CONTAINER_ROLES = Set.of("Part", "Sect", "Div", "Document");
    private static final int MIN_RUN_LENGTH = 3;
    private static final float LEFT_EDGE_TOLERANCE = 2.0f;
    private static final float INDENT_THRESHOLD = 10.0f;

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Listlike Paragraphs Check";
    }

    @Override
    public String description() {
        return "Detects runs of P elements that should be lists";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        if (!CONTAINER_ROLES.contains(ctx.role())) {
            return;
        }
        if (ctx.children().size() < MIN_RUN_LENGTH) {
            return;
        }

        List<List<Integer>> runs = findParagraphRuns(ctx.childRoles());
        for (List<Integer> runIndices : runs) {
            checkRunForListFeatures(ctx, runIndices);
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Finds all maximal runs of 3+ consecutive "P" entries in the child roles list. */
    private List<List<Integer>> findParagraphRuns(List<String> childRoles) {
        List<List<Integer>> runs = new ArrayList<>();
        List<Integer> currentRun = new ArrayList<>();

        for (int i = 0; i < childRoles.size(); i++) {
            if ("P".equals(childRoles.get(i))) {
                currentRun.add(i);
            } else {
                if (currentRun.size() >= MIN_RUN_LENGTH) {
                    runs.add(currentRun);
                }
                currentRun = new ArrayList<>();
            }
        }
        // Don't forget the last run
        if (currentRun.size() >= MIN_RUN_LENGTH) {
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
        int pageNum = ctx.getPageNumber();
        if (pageNum <= 0) {
            return;
        }

        // Collect bounds for each P in the run
        List<Float> leftEdges = new ArrayList<>();
        List<PdfStructElem> elements = new ArrayList<>();
        List<Integer> validIndices = new ArrayList<>();

        for (int idx : runIndices) {
            PdfStructElem p = ctx.children().get(idx);
            Rectangle bounds = Content.getBoundsForElement(p, ctx.docCtx(), pageNum);
            if (bounds != null) {
                leftEdges.add(bounds.getLeft());
                elements.add(p);
                validIndices.add(idx);
            }
        }

        if (elements.size() < MIN_RUN_LENGTH) {
            return;
        }

        // Split into contiguous sub-runs where left edges are consistent
        List<SubRun> subRuns = splitByLeftEdge(leftEdges, elements, validIndices);

        for (SubRun subRun : subRuns) {
            checkSubRunForListFeatures(ctx, subRun, pageNum);
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
            if (length >= MIN_RUN_LENGTH) {
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
    private void checkSubRunForListFeatures(StructTreeContext ctx, SubRun subRun, int pageNum) {
        float runMedianLeft = median(subRun.leftEdges);
        float referenceLeft = getReferenceLeftEdge(ctx, subRun.indices, pageNum);

        if (referenceLeft < 0) {
            logger.debug(
                    "No reference left edge for P sub-run under obj. #{}, skipping",
                    StructureTree.objNum(ctx.node()));
            return;
        }

        float indent = runMedianLeft - referenceLeft;
        if (indent < INDENT_THRESHOLD) {
            logger.debug(
                    "P sub-run under obj. #{} indent {}pt < threshold {}pt, skipping",
                    StructureTree.objNum(ctx.node()),
                    String.format("%.1f", indent),
                    INDENT_THRESHOLD);
            return;
        }

        IssueFix fix = new WrapParagraphRunInList(ctx.node(), subRun.elements);
        Issue issue =
                new Issue(
                        IssueType.LIST_TAGGED_AS_PARAGRAPHS,
                        IssueSev.WARNING,
                        IssueLoc.atElem(ctx.node()),
                        subRun.elements.size()
                                + " consecutive P elements appear to be a list (indented "
                                + String.format("%.0f", indent)
                                + "pt)",
                        fix);
        issues.add(issue);

        logger.debug(
                "Detected suspected list of {} elements under obj. #{} (indent {}pt) on page {}",
                subRun.elements.size(),
                StructureTree.objNum(ctx.node()),
                String.format("%.1f", indent),
                pageNum);
    }

    private record SubRun(
            List<PdfStructElem> elements, List<Integer> indices, List<Float> leftEdges) {}

    /**
     * Gets the minimum left edge from non-run siblings (H1, H2, other P elements, etc.) to use as a
     * reference for indentation comparison.
     */
    private float getReferenceLeftEdge(
            StructTreeContext ctx, List<Integer> runIndices, int pageNum) {
        Set<Integer> runIndexSet = Set.copyOf(runIndices);
        float minLeft = -1;

        for (int i = 0; i < ctx.children().size(); i++) {
            if (runIndexSet.contains(i)) {
                continue;
            }

            PdfStructElem sibling = ctx.children().get(i);
            Rectangle bounds = Content.getBoundsForElement(sibling, ctx.docCtx(), pageNum);
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
