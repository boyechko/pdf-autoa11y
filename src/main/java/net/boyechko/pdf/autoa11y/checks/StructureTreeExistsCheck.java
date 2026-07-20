// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Detects if the structure tree root is present. */
public class StructureTreeExistsCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Structure Tree Exists Check";
    }

    @Override
    public String description() {
        return "Detects if the document has a structure tree root";
    }

    @Override
    public String passedMessage() {
        return "Structure tree root is present";
    }

    @Override
    public String failedMessage() {
        return "Document has no structure tree root";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            Issue issue =
                    new Issue(
                            IssueType.NO_STRUCT_TREE,
                            IssueSev.FATAL,
                            "This PDF has no structure tree. It must be tagged before"
                                    + " accessibility remediation can proceed.",
                            null);
            return new IssueList(issue);
        }

        return new IssueList();
    }
}
