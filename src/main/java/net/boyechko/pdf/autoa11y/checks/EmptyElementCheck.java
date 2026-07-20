// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.EmptyElementFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects structure elements with no content (no MCRs, no OBJRs, no children). Uses {@code
 * leaveElement()} for bottom-up detection so that leaf-empty elements are found first.
 */
public class EmptyElementCheck extends StructTreeCheck {

    private final List<PdfStructElem> emptyElements = new ArrayList<>();
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Empty Element Check";
    }

    @Override
    public String description() {
        return "Structure elements should contain content";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        List<IStructureNode> kids = node.getKids();
        if (kids == null || kids.isEmpty()) {
            emptyElements.add(node);
        }
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        if (!emptyElements.isEmpty()) {
            IssueFix fix = new EmptyElementFix(emptyElements);
            Issue issue =
                    new Issue(
                            IssueType.EMPTY_ELEMENT,
                            IssueSev.WARNING,
                            "Found " + emptyElements.size() + " empty structure element(s)",
                            fix);
            issues.add(issue);
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
