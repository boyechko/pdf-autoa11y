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

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects if the document tab order is set to follow the structure tree order. */
public class TabOrderCheck implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override
    public String name() {
        return "Tab Order Check";
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
    public IssueList findIssues(DocumentContext ctx) {
        if (ctx.doc().getPage(1).getTabOrder() != null) {
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
                        return "Set document tab order to follow the structure tree";
                    }

                    @Override
                    public void apply(DocumentContext c) {
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
