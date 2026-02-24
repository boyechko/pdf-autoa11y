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

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfName;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Reports meaningful Figure elements that lack alt text. These are content images large enough to
 * convey information but without descriptions for screen readers. No automatic fix — these need
 * human-written alt text.
 */
public class MissingAltTextCheck extends StructTreeCheck {
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Missing Alt Text Check";
    }

    @Override
    public String description() {
        return "Content images should have alt text";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        if (!PdfName.Figure.equals(ctx.node().getRole())) {
            return true;
        }
        if (ctx.node().getAlt() != null) {
            return true;
        }

        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return true;
        }

        if (Content.findImageMcidsForElem(ctx.node(), ctx.docCtx()).isEmpty()) {
            return true;
        }

        Rectangle bounds = Content.getBoundsForElement(ctx.node(), ctx.docCtx(), pageNumber);
        if (bounds == null || !MistaggedArtifactCheck.isMeaningfulSize(bounds)) {
            return true;
        }

        Issue issue =
                new Issue(
                        IssueType.FIGURE_MISSING_ALT,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "Figure without alt text");
        issues.add(issue);
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
