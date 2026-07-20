// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class StructureTreeExistsCheckTest extends PdfTestBase {

    @Test
    void detectsMissingStructureTree() throws Exception {
        Path ocrPdf = Path.of("src/test/resources/scanned_and_ocred.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(ocrPdf.toString()))) {
            DocContext ctx = new DocContext(pdfDoc);
            StructureTreeExistsCheck rule = new StructureTreeExistsCheck();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Should detect missing structure tree");
            assertEquals(IssueType.NO_STRUCT_TREE, issues.get(0).type());
            assertEquals(IssueSev.FATAL, issues.get(0).severity());
            assertNull(issues.get(0).fix(), "No automatic fix for missing structure tree");
        }
    }

    @Test
    void taggedDocumentPasses() throws Exception {
        Path taggedPdf = testOutputPath();
        try (PdfWriter writer = new PdfWriter(taggedPdf.toString());
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(new Paragraph("Tagged content"));
        }
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(taggedPdf.toString()))) {
            DocContext ctx = new DocContext(pdfDoc);
            StructureTreeExistsCheck rule = new StructureTreeExistsCheck();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(issues.isEmpty(), "Tagged document should pass");
        }
    }

    @Test
    void blankUntaggedDocumentFails() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();

            DocContext ctx = new DocContext(pdfDoc);
            StructureTreeExistsCheck rule = new StructureTreeExistsCheck();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Blank untagged document should fail");
            assertEquals(IssueSev.FATAL, issues.get(0).severity());
        }
    }
}
