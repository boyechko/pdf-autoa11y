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
package net.boyechko.pdf.autoa11y.visitors;

import com.itextpdf.kernel.pdf.PdfName;
import net.boyechko.pdf.autoa11y.fixes.children.ListifyParagraphOfLinks;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

public class ParagraphOfLinksVisitor implements StructureTreeVisitor {
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Paragraph Of Links Visitor";
    }

    @Override
    public String description() {
        return "P elements containing only links should be converted to L elements";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!PdfName.P.equals(ctx.node().getRole())) {
            return true;
        }

        if (ctx.children().stream().allMatch(c -> c.getRole().equals(PdfName.Link))) {
            IssueFix fix = new ListifyParagraphOfLinks(ctx.node(), ctx.children());
            Issue newIssue =
                    new Issue(
                            IssueType.PARAGRAPH_OF_LINKS,
                            IssueSeverity.ERROR,
                            new IssueLocation(ctx.node()),
                            "Paragraph contains only links",
                            fix);
            issues.add(newIssue);
        }

        return true;
    }

    @Override
    public void leaveElement(VisitorContext ctx) {}

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
