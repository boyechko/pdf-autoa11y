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
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;

class MissingDocumentRuleTest extends PdfTestBase {

    @Test
    void detectsMissingDocument() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            // Create structure tree with Part and Figure at root (no Document)
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem part = new PdfStructElem(pdfDoc, PdfName.Part);
            root.addKid(part);
            PdfStructElem figure = new PdfStructElem(pdfDoc, PdfName.Figure);
            root.addKid(figure);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            MissingDocumentRule rule = new MissingDocumentRule();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Should detect missing Document wrapper");
            Issue issue = issues.get(0);
            assertEquals(IssueType.MISSING_DOCUMENT_WRAPPER, issue.type());
            assertNotNull(issue.fix(), "Should provide a fix");
        }
    }

    @Test
    void noIssueWhenDocumentPresent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            // Create structure tree with Document at root
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            MissingDocumentRule rule = new MissingDocumentRule();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(issues.isEmpty(), "Should not detect issue when Document is present");
        }
    }

    @Test
    void wrapsRootChildrenInDocument() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            // Create structure tree with Part and P at root (no Document)
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem part = new PdfStructElem(pdfDoc, PdfName.Part);
            root.addKid(part);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            root.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            MissingDocumentRule rule = new MissingDocumentRule();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size());
            Issue issue = issues.get(0);
            assertNotNull(issue.fix());

            // Apply the fix
            issue.fix().apply(ctx);

            // Verify Document wrapper was created
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Root should have exactly one child");
            assertTrue(rootKids.get(0) instanceof PdfStructElem);
            PdfStructElem docElem = (PdfStructElem) rootKids.get(0);
            assertEquals("Document", docElem.getRole().getValue());

            // Verify original elements are now under Document
            List<IStructureNode> docKids = docElem.getKids();
            assertEquals(2, docKids.size(), "Document should have two children");
        }
    }

    @Test
    void fixIsIdempotent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem part = new PdfStructElem(pdfDoc, PdfName.Part);
            root.addKid(part);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            MissingDocumentRule rule = new MissingDocumentRule();
            IssueList issues = rule.findIssues(ctx);
            Issue issue = issues.get(0);

            // Apply fix twice
            issue.fix().apply(ctx);
            issue.fix().apply(ctx);

            // Verify still only one Document
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size());
            assertEquals("Document", ((PdfStructElem) rootKids.get(0)).getRole().getValue());
        }
    }
}
