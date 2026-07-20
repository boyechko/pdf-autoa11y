// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Detects if the document tab order is set to follow the structure tree order. */
public class TabOrderCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Tab Order Check";
    }

    @Override
    public String description() {
        return "Detects if the document tab order is set to follow the structure tree order";
    }

    @Override
    public String passedMessage() {
        return "Document tab order is set to follow the structure tree";
    }

    @Override
    public String failedMessage() {
        return "Document tab order is not set to follow the structure tree";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        if (ctx.doc().getPage(1).getTabOrder() != null) {
            return new IssueList();
        }

        IssueFix fix =
                new IssueFix() {
                    @Override
                    public String describe() {
                        return "Set document tab order to follow the structure tree";
                    }

                    @Override
                    public void apply(DocContext c) {
                        int pageCount = c.doc().getNumberOfPages();
                        for (int i = 1; i <= pageCount; i++) {
                            c.doc().getPage(i).setTabOrder(PdfName.S);
                        }
                    }
                };

        Issue issue = new Issue(IssueType.TAB_ORDER_NOT_SET, IssueSev.ERROR, failedMessage(), fix);
        return new IssueList(issue);
    }
}
