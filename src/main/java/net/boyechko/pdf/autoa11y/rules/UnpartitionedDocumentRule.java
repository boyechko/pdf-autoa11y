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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.fixes.NormalizePageParts;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects when direct Document children should be grouped into page-level Part containers. */
public class UnpartitionedDocumentRule implements Rule {
    private static final int MINIMUM_NUMBER_OF_PAGES = 5;

    @Override
    public String name() {
        return "Unpartitioned Document Rule";
    }

    @Override
    public String passedMessage() {
        return "Document content grouped by page Parts";
    }

    @Override
    public String failedMessage() {
        return "Document content is not grouped by page Parts";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        if (!needsNormalization(ctx)) {
            return new IssueList();
        }

        IssueFix fix = new NormalizePageParts();
        Issue issue =
                new Issue(
                        IssueType.PAGE_PARTS_NOT_NORMALIZED,
                        IssueSeverity.WARNING,
                        "Direct Document children should be grouped into page-level Part elements",
                        fix);
        return new IssueList(issue);
    }

    public static boolean needsNormalization(DocumentContext ctx) {
        if (ctx.doc().getNumberOfPages() < MINIMUM_NUMBER_OF_PAGES) {
            return false;
        }

        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return false;
        }

        List<IStructureNode> rootKids = root.getKids();
        if (rootKids == null || rootKids.isEmpty()) {
            return false;
        }

        PdfStructElem documentElem = StructureTree.findFirstChild(root, PdfName.Document);
        if (documentElem == null) {
            return rootKids.stream().anyMatch(kid -> kid instanceof PdfStructElem);
        }

        Map<Integer, PdfStructElem> existingParts = existingPartsByPage(ctx.doc(), documentElem);
        if (existingParts.size() < ctx.doc().getNumberOfPages()) {
            return true;
        }

        List<IStructureNode> documentKids = documentElem.getKids();
        if (documentKids == null) {
            return false;
        }

        for (IStructureNode kid : documentKids) {
            if (kid instanceof PdfStructElem elem && !PdfName.Part.equals(elem.getRole())) {
                int pageNum = StructureTree.determinePageNumber(ctx, elem);
                if (pageNum > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Map<Integer, PdfStructElem> existingPartsByPage(
            PdfDocument doc, PdfStructElem documentElem) {
        Map<Integer, PdfStructElem> byPage = new HashMap<>();
        for (int pageNum = 1; pageNum <= doc.getNumberOfPages(); pageNum++) {
            PdfStructElem part =
                    NormalizePageParts.findPartForPage(documentElem, doc.getPage(pageNum));
            if (part != null) {
                byPage.put(pageNum, part);
            }
        }
        return byPage;
    }
}
