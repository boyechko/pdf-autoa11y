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

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.fixes.RemoveEmptyElements;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeChecker;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects structure elements with no content (no MCRs, no OBJRs, no children). Uses {@code
 * leaveElement()} for bottom-up detection so that leaf-empty elements are found first.
 */
public class EmptyElementCheck extends StructTreeChecker {

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
    public void afterTraversal() {
        if (!emptyElements.isEmpty()) {
            IssueFix fix = new RemoveEmptyElements(emptyElements);
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
