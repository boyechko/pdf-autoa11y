// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.fixes.OrphanedContentFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects MCRs and OBJRs whose page reference no longer resolves -- typically remnants left in the
 * structure tree after a page is deleted in Acrobat without cleaning up the tags.
 */
public class OrphanedContentCheck extends StructTreeCheck {

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Orphaned Content Check";
    }

    @Override
    public String description() {
        return "MCRs and OBJRs should reference content on a valid page";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfMcr mcr && mcr.getPageIndirectReference() == null) {
                IssueFix fix = new OrphanedContentFix(node, mcr);
                Issue issue =
                        new Issue(
                                IssueType.ORPHANED_CONTENT,
                                IssueSev.WARNING,
                                locAtElem(ctx),
                                "Orphaned " + typeOf(mcr) + " in " + ctx.role(),
                                fix);
                issues.add(issue);
            }
        }
    }

    private static String typeOf(PdfMcr mcr) {
        return mcr instanceof PdfObjRef ? "OBJR" : "MCID " + mcr.getMcid();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
