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
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;

class BadlyMappedLigatureRuleTest extends PdfTestBase {

    @Test
    void detectsBrokenLigatureMappingsInCatalogSample() throws Exception {
        Path inputPath = Path.of("src/test/resources/catalog_p1.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPath.toString()))) {
            BadlyMappedLigatureRule rule = new BadlyMappedLigatureRule();
            IssueList issues = rule.findIssues(new DocumentContext(pdfDoc));

            assertFalse(issues.isEmpty(), "Should detect broken ligature mappings");
            Issue issue = issues.get(0);
            assertEquals(IssueType.LIGATURE_MAPPING_BROKEN, issue.type());
            assertNotNull(issue.fix(), "Should provide automatic ligature remapping fix");
        }
    }

    @Test
    void remapsLigaturesSoExtractedWordsAreCorrect() throws Exception {
        Path inputPath = Path.of("src/test/resources/catalog_p1.pdf");
        Path outputPath = testOutputPath("catalog_p1-ligatures-fixed.pdf");

        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(inputPath.toString()),
                        new PdfWriter(outputPath.toString()))) {
            BadlyMappedLigatureRule rule = new BadlyMappedLigatureRule();
            DocumentContext ctx = new DocumentContext(pdfDoc);
            IssueList issues = rule.findIssues(ctx);
            assertFalse(issues.isEmpty(), "Expected at least one ligature-mapping issue");

            for (Issue issue : issues) {
                assertNotNull(issue.fix(), "Issue should be fixable");
                issue.fix().apply(ctx);
            }

            String pageText = pageText(pdfDoc, 1);
            assertTrue(pageText.contains("offers"), "offers should be fully extracted");
            assertTrue(pageText.contains("certificates"), "certificates should be fully extracted");
            assertTrue(pageText.contains("students"), "students should be fully extracted");
            assertTrue(
                    pageText.contains("postbaccalaureate"),
                    "postbaccalaureate should be fully extracted");
            assertTrue(pageText.contains("study"), "study should be fully extracted");
            assertTrue(pageText.contains("just"), "just should be fully extracted");
            assertTrue(pageText.contains("standards"), "standards should be fully extracted");

            assertFalse(pageText.contains("ofers"), "Broken offer text should be fixed");
            assertFalse(
                    pageText.contains("certifcates"), "Broken certificate text should be fixed");
            assertFalse(pageText.contains("sudents"), "Broken student text should be fixed");
            assertFalse(
                    pageText.contains("posbaccalaureate"),
                    "Broken postbaccalaureate text should be fixed");
            assertFalse(pageText.contains("sudy"), "Broken study text should be fixed");
            assertFalse(pageText.contains("jus "), "Broken just text should be fixed");
            assertFalse(pageText.contains("sandards"), "Broken standards text should be fixed");
        }
    }

    @Test
    void noIssueForSimpleTaggedPdf() throws Exception {
        Path testPdf =
                createTestPdf(
                        (pdfDoc, layoutDoc) -> {
                            layoutDoc.add(
                                    new com.itextpdf.layout.element.Paragraph(
                                            "Simple text without ligature mapping problems."));
                        });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testPdf.toString()))) {
            BadlyMappedLigatureRule rule = new BadlyMappedLigatureRule();
            IssueList issues = rule.findIssues(new DocumentContext(pdfDoc));
            assertTrue(
                    issues.isEmpty(), "Simple test PDF should not trigger ligature mapping rule");
        }
    }

    private static String pageText(PdfDocument pdfDoc, int pageNum) {
        Map<Integer, String> byMcid = Content.extractTextForPage(pdfDoc.getPage(pageNum));
        StringBuilder joined = new StringBuilder();
        for (String segment : new TreeMap<>(byMcid).values()) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(segment);
        }
        return joined.toString();
    }
}
