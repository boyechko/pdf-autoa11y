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

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.WrapInDocument;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/**
 * Detects when the structure tree root has no Document child element. Per PDF/UA-1, the root should
 * contain a Document element that wraps all content.
 */
public class MissingDocumentRule implements Rule {

    @Override
    public String name() {
        return "Document Element Present";
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

        // Check if any root child is a Document element
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

        // No Document wrapper found - create issue with fix
        IssueFix fix = new WrapInDocument();
        Issue issue =
                new Issue(
                        IssueType.MISSING_DOCUMENT_WRAPPER,
                        IssueSeverity.ERROR,
                        "Structure tree root has no Document element",
                        fix);
        return new IssueList(issue);
    }
}
