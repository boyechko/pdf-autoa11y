// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Detects if the document language is set. */
public class LanguageSetCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Language Set Check";
    }

    @Override
    public String description() {
        return "Detects if the document language is set";
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
    public IssueList findIssues(DocContext ctx) {
        PdfCatalog cat = ctx.doc().getCatalog();

        if (cat.getLang() != null) {
            // Document language is already set
            return new IssueList();
        }

        IssueFix fix =
                new IssueFix() {
                    @Override
                    public String describe() {
                        return "Set document language to English (en-US)";
                    }

                    @Override
                    public void apply(DocContext c) {
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
