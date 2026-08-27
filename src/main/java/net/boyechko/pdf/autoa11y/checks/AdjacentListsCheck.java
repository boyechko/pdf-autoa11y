// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MergeAdjacentListsFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/** Detects adjacent sibling lists and merges each run into its first list. */
public class AdjacentListsCheck extends StructTreeCheck {

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Adjacent Lists Check";
    }

    @Override
    public String description() {
        return "Adjacent sibling lists should be merged into one list";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        List<IStructureNode> kids = ctx.node().getKids();
        if (kids == null) {
            return true;
        }

        PdfStructElem firstList = null;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem && "L".equals(StructTree.mappedRole(elem))) {
                if (firstList == null) {
                    firstList = elem;
                } else {
                    emitIssue(ctx, firstList, elem);
                }
            } else {
                firstList = null;
            }
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Emits a merge anchored to the first list so runs longer than two collapse leftward. */
    private void emitIssue(
            StructTreeContext ctx, PdfStructElem firstList, PdfStructElem laterList) {
        issues.add(
                new Issue(
                        IssueType.ADJACENT_LISTS,
                        IssueSev.WARNING,
                        locAtElem(ctx, laterList),
                        "Adjacent sibling lists should be one list",
                        new MergeAdjacentListsFix(firstList, laterList)));
    }
}
