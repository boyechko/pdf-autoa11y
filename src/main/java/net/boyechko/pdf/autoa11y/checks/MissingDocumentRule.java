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

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.SetupDocumentStructure;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects if the Document element is the highest-level element in the structure tree. */
public class MissingDocumentRule implements Rule {

    @Override
    public String name() {
        return "Missing Document Rule";
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
    public IssueList findIssues(DocumentContext ctx) {
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

        IssueFix fix = new SetupDocumentStructure();
        Issue issue =
                new Issue(IssueType.MISSING_DOCUMENT_ELEMENT, IssueSev.ERROR, failedMessage(), fix);
        return new IssueList(issue);
    }
}
