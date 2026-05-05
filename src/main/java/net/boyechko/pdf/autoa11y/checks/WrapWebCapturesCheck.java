/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
import com.itextpdf.kernel.pdf.PdfNameTree;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.WrapWebCapturesFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/**
 * Detects PDFs produced by Adobe Web Capture (PDF spec §14.10) — identifiable by a non-empty {@code
 * /Catalog /Names /URLS} name tree — and offers to wrap each captured URL's pages in an {@code
 * <Article>} structure element.
 */
public class WrapWebCapturesCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Wrap Web Captures";
    }

    @Override
    public String description() {
        return "Wrap each Web Capture URL's pages in an Article element";
    }

    @Override
    public String passedMessage() {
        return "No Web Capture URLs to wrap";
    }

    @Override
    public String failedMessage() {
        return "Web Capture URLs present but not wrapped in Article elements";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        PdfNameTree urls = ctx.doc().getCatalog().getNameTree(PdfName.URLS);
        int count = urls.getNames().size();
        if (count == 0) {
            return new IssueList();
        }
        Issue issue =
                new Issue(
                        IssueType.WEB_CAPTURES_NOT_GROUPED,
                        IssueSev.INFO,
                        count + " Web Capture URLs to wrap in Article elements",
                        new WrapWebCapturesFix());
        return new IssueList(issue);
    }
}
