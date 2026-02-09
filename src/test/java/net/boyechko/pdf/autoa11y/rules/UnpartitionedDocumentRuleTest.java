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

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.NormalizePageParts;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;

class UnpartitionedDocumentRuleTest extends PdfTestBase {

    @Test
    void detectsUnbucketedDirectDocumentChildren() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            UnpartitionedDocumentRule rule = new UnpartitionedDocumentRule();
            IssueList issues = rule.findIssues(new DocumentContext(pdfDoc));

            assertEquals(1, issues.size(), "Should report missing page-level Part grouping");
            assertEquals(IssueType.PAGE_PARTS_NOT_NORMALIZED, issues.get(0).type());
            assertTrue(
                    issues.get(0).fix() instanceof NormalizePageParts,
                    "Rule should provide NormalizePageParts fix");
        }
    }

    @Test
    void noIssueWhenDirectChildrenAlreadyUnderParts() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem part1 = new PdfStructElem(pdfDoc, PdfName.Part, page1);
            PdfStructElem part2 = new PdfStructElem(pdfDoc, PdfName.Part, page2);
            document.addKid(part1);
            document.addKid(part2);
            part1.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            part2.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            UnpartitionedDocumentRule rule = new UnpartitionedDocumentRule();
            IssueList issues = rule.findIssues(new DocumentContext(pdfDoc));

            assertTrue(
                    issues.isEmpty(), "Should not report issue for already normalized structure");
        }
    }

    @Test
    void detectsNeedWhenDocumentIsMissingOnMultiPageFile() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            root.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            root.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            UnpartitionedDocumentRule rule = new UnpartitionedDocumentRule();
            IssueList issues = rule.findIssues(new DocumentContext(pdfDoc));

            assertEquals(
                    1,
                    issues.size(),
                    "Rule should still emit NormalizePageParts so it can run after Document setup");
        }
    }
}
