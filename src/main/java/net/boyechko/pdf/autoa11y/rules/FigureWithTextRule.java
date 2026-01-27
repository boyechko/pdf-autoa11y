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

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.ChangeFigureRole;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects Figure elements that contain text content rather than actual images. */
public class FigureWithTextRule implements Rule {
    // If Figure has non-trivial text content, it's likely misclassified
    private int NON_TRIVIAL_TEXT_LENGTH = 30;

    @Override
    public String name() {
        return "Figure Elements Valid";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return new IssueList();
        }

        IssueList issues = new IssueList();
        walkTree(root, ctx, issues);
        return issues;
    }

    private void walkTree(PdfStructTreeRoot root, DocumentContext ctx, IssueList issues) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                checkElement(elem, ctx, issues);
            }
        }
    }

    private void checkElement(PdfStructElem elem, DocumentContext ctx, IssueList issues) {
        String role = elem.getRole().getValue();

        if ("Figure".equals(role)) {
            checkFigure(elem, ctx, issues);
        }

        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    checkElement(childElem, ctx, issues);
                }
            }
        }
    }

    private void checkFigure(PdfStructElem figure, DocumentContext ctx, IssueList issues) {
        int pageNumber = getPageNumber(figure, ctx);
        if (pageNumber == 0) {
            return;
        }

        String textContent = McidTextExtractor.getMcrContentSummary(figure, ctx.doc(), pageNumber);

        if (textContent != null && !textContent.isEmpty() && textContent.length() > 1) {
            IssueFix fix = new ChangeFigureRole(figure, PdfName.P);
            String truncated =
                    textContent.length() > NON_TRIVIAL_TEXT_LENGTH
                            ? textContent.substring(0, NON_TRIVIAL_TEXT_LENGTH) + "..."
                            : textContent;
            Issue issue =
                    new Issue(
                            IssueType.FIGURE_WITH_TEXT,
                            IssueSeverity.WARNING,
                            new IssueLocation(figure),
                            "Figure contains text: \"" + truncated + "\"",
                            fix);
            issues.add(issue);
        }
    }

    private int getPageNumber(PdfStructElem elem, DocumentContext ctx) {
        PdfDictionary dict = elem.getPdfObject();
        PdfDictionary pg = dict.getAsDictionary(PdfName.Pg);

        if (pg != null) {
            return ctx.doc().getPageNumber(pg);
        }

        // Try to get from object mapping
        if (elem.getPdfObject().getIndirectReference() != null) {
            int objNum = elem.getPdfObject().getIndirectReference().getObjNumber();
            return ctx.getPageNumber(objNum);
        }

        return 0;
    }
}
