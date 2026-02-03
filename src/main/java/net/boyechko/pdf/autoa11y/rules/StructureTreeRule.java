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

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects if the structure tree root is present. */
public class StructureTreeRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override
    public String name() {
        return "Structure tree should be present";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            Issue issue =
                    new Issue(
                            IssueType.NO_STRUCT_TREE,
                            IssueSeverity.ERROR,
                            "Document has no structure tree (StructTreeRoot is null)",
                            null);
            return new IssueList(issue);
        }

        return new IssueList();
    }
}
