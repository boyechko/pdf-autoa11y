// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.MissingDocumentFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Detects if the Document element is the highest-level element in the structure tree. */
public class MissingDocumentCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Missing Document Check";
    }

    @Override
    public String description() {
        return "Detects if the structure tree root contains a Document element as the single child";
    }

    @Override
    public String passedMessage() {
        return "Structure tree root has Document element";
    }

    @Override
    public String failedMessage() {
        return "Structure tree root missing Document element";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return new IssueList();
        }

        List<IStructureNode> kids = root.getKids();
        if (kids == null || kids.isEmpty()) {
            return new IssueList();
        }

        boolean hasDocument = false;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                String role = elem.getRole().getValue();
                if ("Document".equals(role)) {
                    hasDocument = true;
                    break;
                }
            }
        }

        if (hasDocument) {
            return new IssueList();
        }

        IssueFix fix = new MissingDocumentFix();
        Issue issue =
                new Issue(IssueType.MISSING_DOCUMENT_ELEMENT, IssueSev.ERROR, failedMessage(), fix);
        return new IssueList(issue);
    }
}
