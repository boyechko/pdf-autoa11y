// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later

package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.StructTree.Node;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class MistaggedListCheckTest extends PdfTestBase {

    private static void walkWith(PdfDocument pdfDoc, MistaggedListCheck check) throws Exception {
        StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
        walker.addVisitor(check);
        walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }

    // == Indent evidence =================================================

    private Path createTestPdfWithParagraphRun(
            int numParagraphs,
            String paragraphText,
            float headingMargin,
            float paragraphMargin,
            int paragraphIndent)
            throws Exception {
        Path pdfPath = testOutputPath();
        try (PdfWriter writer = new PdfWriter(pdfPath.toString());
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            Style headingStyle =
                    new Style().setFont(font).setFontSize(18).setMarginLeft(headingMargin);
            Style paragraphStyle =
                    new Style().setFont(font).setFontSize(12).setMarginLeft(paragraphMargin);

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
        }
        return pdfPath;
    }

    @Test
    void noIssuesWhenNoReferenceLeftEdgeExists() throws Exception {
        // 1 heading + 2 first-line-indent paragraphs = 3 P elements, all at same edge.
        // They form one run, but no non-run siblings exist → no reference → skipped.
        Path pdfFile = createTestPdfWithParagraphRun(0, "text", 0, 0, 0);
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            walkWith(pdfDoc, check);
        }

        assertTrue(check.getIssues().isEmpty(), "Should have no issues");
    }

    @Test
    void detectsIndentedParagraphRun() throws Exception {
        // Heading at page margin (~36pt), paragraphs indented 30pt further right (~66pt).
        // 5 + 2 = 7 indented P elements form a sub-run; heading serves as reference.
        // indent = 30pt > 10pt threshold → detected.
        String paragraphText = "These paragraphs are indented relative to the heading.";
        Path pdfFile = createTestPdfWithParagraphRun(5, paragraphText, 0, 30, 0);
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            walkWith(pdfDoc, check);
        }

        assertEquals(1, check.getIssues().size(), "Should have 1 issue");
        Issue issue = check.getIssues().get(0);
        assertEquals(IssueType.LIST_TAGGED_AS_PARAGRAPHS, issue.type());
    }

    @Test
    void noIssuesWhenParagraphsNotIndented() throws Exception {
        // Heading at margin 30pt (~66pt), paragraphs at margin 0pt (~36pt).
        // Paragraphs are LESS indented than heading → negative indent → not detected.
        String paragraphText = "These paragraphs are not indented relative to the heading.";
        Path pdfFile = createTestPdfWithParagraphRun(5, paragraphText, 30, 0, 0);
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            walkWith(pdfDoc, check);
        }

        assertTrue(
                check.getIssues().isEmpty(),
                "Should have no issues when paragraphs are not indented");
    }

    // == Link evidence ===================================================

    @Test
    void detectsAndFixesParagraphOfLinks() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            Document layoutDoc = new Document(pdfDoc);
            Paragraph p = new Paragraph();
            for (int i = 0; i < 5; i++) {
                Link link = new Link("Link " + i, PdfAction.createURI("https://uw.edu"));
                p.add(link);
            }
            layoutDoc.add(p);

            walkWith(pdfDoc, check);

            assertEquals(1, check.getIssues().size(), "Should have 1 issue");
            Issue issue = check.getIssues().get(0);
            assertEquals(
                    IssueType.PARAGRAPH_OF_LINKS,
                    issue.type(),
                    "Issue type should be PARAGRAPH_OF_LINKS");

            issue.fix().apply(new DocContext(pdfDoc));

            Node<String> roleTree =
                    StructTree.toRoleTree(StructTree.findDocument(pdfDoc.getStructTreeRoot()));
            assertEquals(
                    "Document[L[LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]]]]",
                    roleTree.toString());
            layoutDoc.close();
        }
    }

    @Test
    void ignoresParagraphsWithIntermixedLinks() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            for (int i = 0; i < 5; i++) {
                p.add(new Link("Link " + i, PdfAction.createURI("https://uw.edu")));
                p.add(new Text("Text between links"));
            }
            layoutDoc.add(p);
            assertEquals(
                    "Document[P[Link[],Span[],Link[],Span[],Link[],Span[],Link[],Span[],Link[],Span[]]]",
                    StructTree.toRoleTreeString(
                            StructTree.findDocument(pdfDoc.getStructTreeRoot())));

            walkWith(pdfDoc, check);
            assertEquals(0, check.getIssues().size(), "Should have 0 issues");
        }
    }
}
