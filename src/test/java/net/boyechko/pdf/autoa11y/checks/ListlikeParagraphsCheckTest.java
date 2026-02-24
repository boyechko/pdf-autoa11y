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

package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class ListlikeParagraphsCheckTest extends PdfTestBase {

    private Path createTestPdfWithParagraphRun(
            int numParagraphs,
            String paragraphText,
            float headingMargin,
            float paragraphMargin,
            int paragraphIndent)
            throws Exception {
        return createTestPdf(
                (pdfDoc, layoutDoc) -> {
                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    Style headingStyle =
                            new Style().setFont(font).setFontSize(18).setMarginLeft(headingMargin);
                    Style paragraphStyle =
                            new Style()
                                    .setFont(font)
                                    .setFontSize(12)
                                    .setMarginLeft(paragraphMargin);

                    Text headingText = new Text("Heading 1").addStyle(headingStyle);
                    Paragraph heading = new Paragraph(headingText);
                    layoutDoc.add(heading);

                    for (int i = 0; i < numParagraphs; i++) {
                        Paragraph p = new Paragraph(paragraphText).addStyle(paragraphStyle);
                        layoutDoc.add(p);
                    }

                    String longText =
                            new StringBuilder()
                                    .append("Paragraph with first line indent")
                                    .append("Paragraph with first line indent")
                                    .append(paragraphText)
                                    .toString();
                    for (int i = 0; i < 2; i++) {
                        Paragraph p =
                                new Paragraph(longText)
                                        .addStyle(paragraphStyle)
                                        .setFirstLineIndent(paragraphIndent);
                        layoutDoc.add(p);
                    }
                });
    }

    @Test
    void noIssuesWhenNoReferenceLeftEdgeExists() throws Exception {
        // 1 heading + 2 first-line-indent paragraphs = 3 P elements, all at same edge.
        // They form one run, but no non-run siblings exist → no reference → skipped.
        Path pdfFile = createTestPdfWithParagraphRun(0, "text", 0, 0, 0);
        ListlikeParagraphsCheck visitor = new ListlikeParagraphsCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(visitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
        }

        assertTrue(visitor.getIssues().isEmpty(), "Should have no issues");
    }

    @Test
    void detectsIndentedParagraphRun() throws Exception {
        // Heading at page margin (~36pt), paragraphs indented 30pt further right (~66pt).
        // 5 + 2 = 7 indented P elements form a sub-run; heading serves as reference.
        // indent = 30pt > 10pt threshold → detected.
        String paragraphText = "These paragraphs are indented relative to the heading.";
        Path pdfFile = createTestPdfWithParagraphRun(5, paragraphText, 0, 30, 0);
        ListlikeParagraphsCheck visitor = new ListlikeParagraphsCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(visitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
        }

        assertEquals(1, visitor.getIssues().size(), "Should have 1 issue");
        Issue issue = visitor.getIssues().get(0);
        assertEquals(IssueType.LIST_TAGGED_AS_PARAGRAPHS, issue.type());
    }

    @Test
    void noIssuesWhenParagraphsNotIndented() throws Exception {
        // Heading at margin 30pt (~66pt), paragraphs at margin 0pt (~36pt).
        // Paragraphs are LESS indented than heading → negative indent → not detected.
        String paragraphText = "These paragraphs are not indented relative to the heading.";
        Path pdfFile = createTestPdfWithParagraphRun(5, paragraphText, 30, 0, 0);
        ListlikeParagraphsCheck visitor = new ListlikeParagraphsCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(visitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
        }

        assertTrue(
                visitor.getIssues().isEmpty(),
                "Should have no issues when paragraphs are not indented");
    }
}
