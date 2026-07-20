// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.fixes.InconsistentParentTreeFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects structure elements whose marked-content kids are not reflected in the page ParentTree --
 * the /K forward references exist and the content renders, but the reverse index (the page's
 * ParentTree entry) is missing or points at a different element. Such gaps make the content
 * invisible to consumers that rebuild structure from the ParentTree, including iText's page
 * extraction and Acrobat, and are flagged by preflight as "inconsistent ParentTree mapping".
 */
public class InconsistentParentTreeCheck extends StructTreeCheck {

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Inconsistent ParentTree Check";
    }

    @Override
    public String description() {
        return "Marked content should be reachable from the page ParentTree";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return;

        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        int broken = 0;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfMcr mcr
                    && InconsistentParentTreeFix.needsReregistration(root, node, mcr)) {
                broken++;
            }
        }
        if (broken == 0) return;

        IssueFix fix = new InconsistentParentTreeFix(node);
        issues.add(
                new Issue(
                        IssueType.INCONSISTENT_PARENT_TREE,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        broken
                                + " marked-content reference(s) missing from ParentTree in "
                                + ctx.role(),
                        fix));
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
