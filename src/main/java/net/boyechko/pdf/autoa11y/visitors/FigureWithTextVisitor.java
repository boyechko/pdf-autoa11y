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
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.fixes.ChangeFigureRole;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/** Visitor that detects Figure elements containing text content rather than actual images. */
public class FigureWithTextVisitor implements StructureTreeVisitor {

    private static final int NON_TRIVIAL_TEXT_LENGTH = 30;
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Figure Elements Valid";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!ctx.hasRole("Figure")) {
            return true;
        }

        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return true;
        }

        String textContent =
                McidTextExtractor.getMcrContentSummary(ctx.node(), ctx.doc(), pageNumber);

        if (textContent != null && !textContent.isEmpty() && textContent.length() > 1) {
            IssueFix fix = new ChangeFigureRole(ctx.node(), PdfName.P);
            String truncated =
                    textContent.length() > NON_TRIVIAL_TEXT_LENGTH
                            ? textContent.substring(0, NON_TRIVIAL_TEXT_LENGTH) + "..."
                            : textContent;
            Issue issue =
                    new Issue(
                            IssueType.FIGURE_WITH_TEXT,
                            IssueSeverity.WARNING,
                            new IssueLocation(ctx.node()),
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
