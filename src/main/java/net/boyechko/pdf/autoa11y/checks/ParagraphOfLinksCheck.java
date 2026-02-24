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

import com.itextpdf.kernel.pdf.PdfName;
import net.boyechko.pdf.autoa11y.fixes.children.ListifyParagraphOfLinks;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeChecker;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

public class ParagraphOfLinksCheck implements StructTreeChecker {
    private static final int MIN_LINKS_COUNT = 2;
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Paragraph Of Links Check";
    }

    @Override
    public String description() {
        return "P elements containing only links should be converted to L elements";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        if (ctx.children().size() < MIN_LINKS_COUNT) {
            return true;
        }

        // Skip if the element has non-struct-elem kids (MCRs/OBJRs) that would
        // be orphaned when we convert Link children to LI > LBody > Link.
        var allKids = ctx.node().getKids();
        if (allKids != null && allKids.size() != ctx.children().size()) {
            return true;
        }

        if (ctx.children().stream().allMatch(c -> c.getRole().equals(PdfName.Link))) {
            IssueFix fix = new ListifyParagraphOfLinks(ctx.node(), ctx.children());
            Issue newIssue =
                    new Issue(
                            IssueType.PARAGRAPH_OF_LINKS,
                            IssueSev.ERROR,
                            IssueLoc.atElem(ctx.node()),
                            "Paragraph contains only links",
                            fix);
            issues.add(newIssue);
        }

        return true;
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {}

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
