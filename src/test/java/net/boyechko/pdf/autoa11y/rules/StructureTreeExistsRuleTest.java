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
import com.itextpdf.layout.element.Paragraph;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class StructureTreeExistsRuleTest extends PdfTestBase {

    @Test
    void detectsMissingStructureTree() throws Exception {
        Path ocrPdf = Path.of("src/test/resources/scanned_and_ocred.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(ocrPdf.toString()))) {
            DocumentContext ctx = new DocumentContext(pdfDoc);
            StructureTreeExistsRule rule = new StructureTreeExistsRule();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Should detect missing structure tree");
            assertEquals(IssueType.NO_STRUCT_TREE, issues.get(0).type());
            assertEquals(IssueSev.FATAL, issues.get(0).severity());
            assertNull(issues.get(0).fix(), "No automatic fix for missing structure tree");
        }
    }

    @Test
    void taggedDocumentPasses() throws Exception {
        Path taggedPdf =
                createTestPdf(
                        (pdfDoc, layoutDoc) -> {
                            layoutDoc.add(new Paragraph("Tagged content"));
                        });
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(taggedPdf.toString()))) {
            DocumentContext ctx = new DocumentContext(pdfDoc);
            StructureTreeExistsRule rule = new StructureTreeExistsRule();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(issues.isEmpty(), "Tagged document should pass");
        }
    }

    @Test
    void blankUntaggedDocumentFails() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();

            DocumentContext ctx = new DocumentContext(pdfDoc);
            StructureTreeExistsRule rule = new StructureTreeExistsRule();
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Blank untagged document should fail");
            assertEquals(IssueSev.FATAL, issues.get(0).severity());
        }
    }
}
