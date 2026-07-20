// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.fixes.FigureWithTextFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/** Detects Figure elements containing text content rather than actual images. */
public class FigureWithTextCheck extends StructTreeCheck {
    private static final int MAX_DISPLAY_LENGTH = 30;
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Figure With Text Check";
    }

    @Override
    public String description() {
        return "Figure elements should not contain text content";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        if (!PdfName.Figure.equals(ctx.node().getRole())) {
            return true;
        }

        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return true;
        }

        String textContent = Content.getTextForElement(ctx.node(), ctx.docCtx(), pageNumber);

        if (textContent != null && !textContent.isEmpty() && textContent.length() > 1) {
            IssueFix fix = new FigureWithTextFix(ctx.node(), PdfName.P);
            String truncated =
                    textContent.length() > MAX_DISPLAY_LENGTH
                            ? textContent.substring(0, MAX_DISPLAY_LENGTH) + "…"
                            : textContent;
            Issue issue =
                    new Issue(
                            IssueType.FIGURE_WITH_TEXT,
                            IssueSev.WARNING,
                            locAtElem(ctx),
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
