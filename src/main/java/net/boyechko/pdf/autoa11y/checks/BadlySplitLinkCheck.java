// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Link;
import net.boyechko.pdf.autoa11y.fixes.MergeSplitLinksFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects a single logical link that an authoring tool split across several {@code <Link>} tags —
 * typically because the link text wrapped across lines, leaving one annotation per line. A screen
 * reader announces each tag as a separate link, so the same destination is offered two or more
 * times in a row.
 *
 * <p>A run qualifies only when the Link tags are immediately adjacent kids (intervening text means
 * they delimit distinct phrases) and all of them resolve to one destination, {@code /PA} original
 * URI included, so that links differing only by URL fragment stay separate. Links carrying their
 * own {@code /Alt} or {@code /ActualText} are left alone, since merging would discard one of the
 * text equivalents.
 */
public class BadlySplitLinkCheck extends StructTreeCheck {

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Badly Split Link Check";
    }

    @Override
    public String description() {
        return "One link split across several Link tags should be a single Link";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        List<IStructureNode> kids = ctx.node().getKids();
        if (kids == null || kids.size() < 2) {
            return true;
        }

        List<PdfStructElem> run = new ArrayList<>();
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem && isMergeableLink(elem)) {
                run.add(elem);
            } else {
                emitSharedDestinationRuns(ctx, run);
                run = new ArrayList<>();
            }
        }
        emitSharedDestinationRuns(ctx, run);

        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** True for a Link tag whose own text equivalent would not be lost by a merge. */
    private static boolean isMergeableLink(PdfStructElem elem) {
        if (!PdfName.Link.equals(elem.getRole())) {
            return false;
        }
        return elem.getPdfObject().get(PdfName.Alt) == null
                && elem.getPdfObject().get(PdfName.ActualText) == null;
    }

    /**
     * Emits one issue per maximal stretch of the run that shares a destination, so two split links
     * sitting back to back become two merges rather than one.
     */
    private void emitSharedDestinationRuns(StructTreeContext ctx, List<PdfStructElem> run) {
        int start = 0;
        while (start < run.size() - 1) {
            int end = start + 1;
            while (end < run.size() && Link.allShareOneDestination(run.subList(start, end + 1))) {
                end++;
            }
            if (end - start > 1) {
                emitIssue(ctx, new ArrayList<>(run.subList(start, end)));
            }
            start = end;
        }
    }

    private void emitIssue(StructTreeContext ctx, List<PdfStructElem> links) {
        issues.add(
                new Issue(
                        IssueType.LINK_SPLIT_ACROSS_TAGS,
                        IssueSev.WARNING,
                        locAtElem(ctx, links.get(0)),
                        "One link split across " + links.size() + " Link tags",
                        new MergeSplitLinksFix(links)));
    }
}
