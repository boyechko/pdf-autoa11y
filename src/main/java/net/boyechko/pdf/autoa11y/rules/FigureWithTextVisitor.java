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
package net.boyechko.pdf.autoa11y.rules;

import com.itextpdf.kernel.pdf.PdfName;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.fixes.ChangeFigureRole;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/** Detects Figure elements containing text content rather than actual images. */
public class FigureWithTextVisitor implements StructureTreeVisitor {
    private static final int MAX_DISPLAY_LENGTH = 30;
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Figure With Text Visitor";
    }

    @Override
    public String description() {
        return "Figure elements should not contain text content";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!PdfName.Figure.equals(ctx.node().getRole())) {
            return true;
        }

        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return true;
        }

        String textContent = Content.getTextForElement(ctx.node(), ctx.docCtx(), pageNumber);

        if (textContent != null && !textContent.isEmpty() && textContent.length() > 1) {
            IssueFix fix = new ChangeFigureRole(ctx.node(), PdfName.P);
            String truncated =
                    textContent.length() > MAX_DISPLAY_LENGTH
                            ? textContent.substring(0, MAX_DISPLAY_LENGTH) + "…"
                            : textContent;
            Issue issue =
                    new Issue(
                            IssueType.FIGURE_WITH_TEXT,
                            IssueSev.WARNING,
                            IssueLoc.atElem(ctx.node()),
                            "Figure contains text: \"" + truncated + "\"",
                            fix);
            issues.add(issue);
        }

        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
