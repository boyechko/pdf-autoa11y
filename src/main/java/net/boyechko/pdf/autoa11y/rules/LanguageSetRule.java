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

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects if the document language is set. */
public class LanguageSetRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override
    public String name() {
        return "Language Set Rule";
    }

    @Override
    public String passedMessage() {
        return "Document-level language attribute is set";
    }

    @Override
    public String failedMessage() {
        return "Document-level language attribute is not set";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfCatalog cat = ctx.doc().getCatalog();

        if (cat.getLang() != null) {
            // Document language is already set
            return new IssueList();
        }

        IssueFix fix =
                new IssueFix() {
                    @Override
                    public int priority() {
                        return P_DOC_SETUP;
                    }

                    @Override
                    public String describe() {
                        return "Set document language to English (en-US)";
                    }

                    @Override
                    public void apply(DocumentContext c) {
                        PdfCatalog cat2 = c.doc().getCatalog();
                        cat2.put(
                                PdfName.Lang,
                                new PdfString("en-US")); // Default to English if not set
                    }
                };

        Issue issue = new Issue(IssueType.LANGUAGE_NOT_SET, IssueSev.ERROR, failedMessage(), fix);
        return new IssueList(issue);
    }
}
