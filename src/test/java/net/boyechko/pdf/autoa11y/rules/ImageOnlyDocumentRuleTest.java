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
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;

class ImageOnlyDocumentRuleTest extends PdfTestBase {
    private static final Path IMAGE_ONLY_DOCUMENT = Path.of("src/test/resources/image_only.pdf");
    private static final Path SCANNED_AND_OCRED_DOCUMENT =
            Path.of("src/test/resources/scanned_and_ocred.pdf");

    @Test
    void detectsImageOnlyDocument() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(IMAGE_ONLY_DOCUMENT.toString()))) {
            DocumentContext ctx = new DocumentContext(pdfDoc);
            ImageOnlyDocumentRule rule = new ImageOnlyDocumentRule();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Should detect image-only document");
            assertEquals(IssueType.IMAGE_ONLY_DOCUMENT, issues.get(0).type());
            assertEquals(IssueSeverity.FATAL, issues.get(0).severity());
            assertNull(issues.get(0).fix(), "No automatic fix for image-only documents");
        }
    }

    @Test
    void imageOnlyDocumentWithTextIsNotImageOnly() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(SCANNED_AND_OCRED_DOCUMENT.toString()))) {
            DocumentContext ctx = new DocumentContext(pdfDoc);
            ImageOnlyDocumentRule rule = new ImageOnlyDocumentRule();
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

            DocumentContext ctx = new DocumentContext(pdfDoc);
            ImageOnlyDocumentRule rule = new ImageOnlyDocumentRule();
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

            DocumentContext ctx = new DocumentContext(pdfDoc);
            ImageOnlyDocumentRule rule = new ImageOnlyDocumentRule();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(
                    issues.isEmpty(),
                    "Untagged document with text should not be flagged as image-only");
        }
    }
}
