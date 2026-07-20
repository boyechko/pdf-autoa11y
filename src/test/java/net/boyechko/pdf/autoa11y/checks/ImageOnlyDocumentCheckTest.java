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

class ImageOnlyDocumentCheckTest extends PdfTestBase {
    private static final Path IMAGE_ONLY_DOCUMENT = Path.of("src/test/resources/image_only.pdf");
    private static final Path SCANNED_AND_OCRED_DOCUMENT =
            Path.of("src/test/resources/scanned_and_ocred.pdf");

    @Test
    void detectsImageOnlyDocument() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(IMAGE_ONLY_DOCUMENT.toString()))) {
            DocContext ctx = new DocContext(pdfDoc);
            ImageOnlyDocumentCheck rule = new ImageOnlyDocumentCheck();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Should detect image-only document");
            assertEquals(IssueType.IMAGE_ONLY_DOCUMENT, issues.get(0).type());
            assertEquals(IssueSev.FATAL, issues.get(0).severity());
            assertNull(issues.get(0).fix(), "No automatic fix for image-only documents");
        }
    }

    @Test
    void imageOnlyDocumentWithTextIsNotImageOnly() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(SCANNED_AND_OCRED_DOCUMENT.toString()))) {
            DocContext ctx = new DocContext(pdfDoc);
            ImageOnlyDocumentCheck rule = new ImageOnlyDocumentCheck();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(
                    issues.isEmpty(),
                    "Scanned and OCRed document should not be flagged as image-only");
        }
    }

    @Test
    void blankDocumentIsNotImageOnly() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();

            DocContext ctx = new DocContext(pdfDoc);
            ImageOnlyDocumentCheck rule = new ImageOnlyDocumentCheck();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(issues.isEmpty(), "Blank document should not be flagged as image-only");
        }
    }

    @Test
    void untaggedDocumentWithTextIsNotImageOnly() throws Exception {
        // Create a PDF with text content but no tagging
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            layoutDoc.add(new Paragraph("This document has text but no tags."));

            DocContext ctx = new DocContext(pdfDoc);
            ImageOnlyDocumentCheck rule = new ImageOnlyDocumentCheck();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(
                    issues.isEmpty(),
                    "Untagged document with text should not be flagged as image-only");
        }
    }
}
