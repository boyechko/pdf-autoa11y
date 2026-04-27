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
import net.boyechko.pdf.autoa11y.fixes.InlineDestinationsFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/**
 * Detects presence of named destinations in the /Catalog /Names /Dests tree, which Web Capture and
 * similar HTML-to-PDF tools generate in large quantities (one per HTML anchor). The fix replaces
 * each named-destination reference in annotations and outlines with an explicit page destination.
 */
public class InlineDestinationsCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Inline Destinations";
    }

    @Override
    public String description() {
        return "Replace named destinations with explicit page references";
    }

    @Override
    public String passedMessage() {
        return "No named destinations present";
    }

    @Override
    public String failedMessage() {
        return "Named destinations present in /Catalog /Names /Dests";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        PdfNameTree dests = ctx.doc().getCatalog().getNameTree(PdfName.Dests);
        int count = dests.getNames().size();
        if (count == 0) {
            return new IssueList();
        }
        Issue issue =
                new Issue(
                        IssueType.NAMED_DESTINATIONS_PRESENT,
                        IssueSev.INFO,
                        count + " named destinations present",
                        new InlineDestinationsFix());
        return new IssueList(issue);
    }
}
