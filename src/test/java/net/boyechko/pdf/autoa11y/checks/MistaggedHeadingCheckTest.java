/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class MistaggedHeadingCheckTest extends PdfTestBase {

    @Test
    void detectsLargeFontParagraphsAsHeadings() throws Exception {
        Path pdfFile = testOutputPath("detectsLargeFontParagraphsAsHeadings.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem docElem = newDocument(pdfDoc);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfCanvas canvas = new PdfCanvas(page);

            addTaggedText(pdfDoc, page, docElem, canvas, font, 24, "Chapter One", 700);
            addTaggedText(pdfDoc, page, docElem, canvas, font, 18, "Section Title", 660);

            // Body text: lots of 12pt content to dominate the histogram
            for (int i = 0; i < 10; i++) {
                addTaggedText(
                        pdfDoc,
                        page,
                        docElem,
                        canvas,
                        font,
                        12,
                        "This is body text paragraph number "
                                + (i + 1)
                                + " with enough content to dominate the histogram.",
                        630 - i * 20);
            }
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MistaggedHeadingCheck().findIssues(new DocContext(pdfDoc));

            long headingIssues =
                    issues.stream().filter(i -> i.type() == IssueType.MISTAGGED_HEADING).count();
            assertTrue(headingIssues >= 2, "Should detect at least 2 heading candidates");
        }
    }

    @Test
    void doesNotFlagBodyTextParagraphs() throws Exception {
        Path pdfFile = testOutputPath("doesNotFlagBodyTextParagraphs.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem docElem = newDocument(pdfDoc);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfCanvas canvas = new PdfCanvas(page);

            for (int i = 0; i < 5; i++) {
                addTaggedText(
                        pdfDoc,
                        page,
                        docElem,
                        canvas,
                        font,
                        12,
                        "All text is the same size in this document paragraph " + (i + 1),
                        700 - i * 20);
            }
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MistaggedHeadingCheck().findIssues(new DocContext(pdfDoc));

            long headingIssues =
                    issues.stream().filter(i -> i.type() == IssueType.MISTAGGED_HEADING).count();
            assertEquals(0, headingIssues, "No headings in uniform-font document");
        }
    }

    @Test
    void correctlyLeveledHeadingIsNotFlagged() throws Exception {
        Path pdfFile = testOutputPath("correctlyLeveledHeadingIsNotFlagged.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem docElem = newDocument(pdfDoc);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfCanvas canvas = new PdfCanvas(page);

            // Largest text, already tagged H1 -- its computed level matches its role.
            addTaggedText(pdfDoc, page, docElem, canvas, font, 24, "Chapter One", PdfName.H1, 700);

            for (int i = 0; i < 5; i++) {
                addTaggedText(
                        pdfDoc,
                        page,
                        docElem,
                        canvas,
                        font,
                        12,
                        "Body text paragraph " + (i + 1) + " with sufficient length to dominate.",
                        660 - i * 20);
            }
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MistaggedHeadingCheck().findIssues(new DocContext(pdfDoc));

            long headingIssues =
                    issues.stream().filter(i -> i.type() == IssueType.MISTAGGED_HEADING).count();
            assertEquals(
                    0, headingIssues, "Heading already at the correct level should not be flagged");
        }
    }

    @Test
    void misleveledHeadingIsFlagged() throws Exception {
        Path pdfFile = testOutputPath("misleveledHeadingIsFlagged.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem docElem = newDocument(pdfDoc);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfCanvas canvas = new PdfCanvas(page);

            // Largest text tagged H2, but as the biggest size it should be H1.
            addTaggedText(pdfDoc, page, docElem, canvas, font, 24, "Chapter One", PdfName.H2, 700);

            for (int i = 0; i < 5; i++) {
                addTaggedText(
                        pdfDoc,
                        page,
                        docElem,
                        canvas,
                        font,
                        12,
                        "Body text paragraph " + (i + 1) + " with sufficient length to dominate.",
                        660 - i * 20);
            }
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MistaggedHeadingCheck().findIssues(new DocContext(pdfDoc));

            long headingIssues =
                    issues.stream().filter(i -> i.type() == IssueType.MISTAGGED_HEADING).count();
            assertEquals(1, headingIssues, "A heading tagged at the wrong level should be flagged");
        }
    }

    @Test
    void skippedHeadingLevelIsFlaggedNotRetagged() throws Exception {
        Path pdfFile = testOutputPath("skippedHeadingLevelIsFlaggedNotRetagged.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem docElem = newDocument(pdfDoc);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfCanvas canvas = new PdfCanvas(page);

            // Sizes 24/20/16/14 rank as H1/H2/H3/H4. In reading order H1, H2, then the 14pt (H4)
            // element appears before any H3 -- a skipped level.
            addTaggedText(pdfDoc, page, docElem, canvas, font, 24, "Heading One", 740);
            addTaggedText(pdfDoc, page, docElem, canvas, font, 20, "Heading Two", 710);
            addTaggedText(pdfDoc, page, docElem, canvas, font, 14, "Skipped Level", 680);
            addTaggedText(pdfDoc, page, docElem, canvas, font, 16, "Heading Three", 650);

            for (int i = 0; i < 8; i++) {
                addTaggedText(
                        pdfDoc,
                        page,
                        docElem,
                        canvas,
                        font,
                        10,
                        "Body text paragraph " + (i + 1) + " with enough content to dominate.",
                        620 - i * 18);
            }
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MistaggedHeadingCheck().findIssues(new DocContext(pdfDoc));

            List<Issue> retags =
                    issues.stream().filter(i -> i.type() == IssueType.MISTAGGED_HEADING).toList();
            List<Issue> nestingIssues =
                    issues.stream()
                            .filter(i -> i.type() == IssueType.IMPROPERLY_NESTED_HEADING)
                            .toList();
            assertEquals(3, retags.size(), "retag H1, H2, and H3");
            assertEquals(1, nestingIssues.size(), "skipped H4 is flagged, not retagged");
            assertTrue(
                    nestingIssues.getFirst().message().contains("Improperly nested H4"),
                    "flagged at the skipped level H4");
        }
    }

    @Test
    void headingTooDeepToStartIsFlagged() throws Exception {
        Path pdfFile = testOutputPath("headingTooDeepToStartIsFlagged.pdf");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem docElem = newDocument(pdfDoc);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfCanvas canvas = new PdfCanvas(page);

            // Sizes 24/20/16 rank as H1/H2/H3. The 16pt (H3) element comes first, before any H1 --
            // a heading too deep to open the outline.
            addTaggedText(pdfDoc, page, docElem, canvas, font, 16, "Premature Sub", 740);
            addTaggedText(pdfDoc, page, docElem, canvas, font, 24, "Heading One", 710);
            addTaggedText(pdfDoc, page, docElem, canvas, font, 20, "Heading Two", 680);

            for (int i = 0; i < 8; i++) {
                addTaggedText(
                        pdfDoc,
                        page,
                        docElem,
                        canvas,
                        font,
                        10,
                        "Body text paragraph " + (i + 1) + " with enough content to dominate.",
                        650 - i * 18);
            }
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MistaggedHeadingCheck().findIssues(new DocContext(pdfDoc));

            List<Issue> retags =
                    issues.stream().filter(i -> i.type() == IssueType.MISTAGGED_HEADING).toList();
            List<Issue> nestingIssues =
                    issues.stream()
                            .filter(i -> i.type() == IssueType.IMPROPERLY_NESTED_HEADING)
                            .toList();
            assertEquals(2, retags.size(), "retag H1 and H2");
            assertEquals(1, nestingIssues.size(), "premature H3 is flagged, not retagged");
            assertTrue(
                    nestingIssues.getFirst().message().contains("Improperly nested H3"),
                    "flagged at the premature level H3");
        }
    }

    // == Helpers =========================================================

    private static PdfStructElem newDocument(PdfDocument pdfDoc) {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        root.addKid(document);
        return document;
    }

    private static void addTaggedText(
            PdfDocument pdfDoc,
            PdfPage page,
            PdfStructElem parent,
            PdfCanvas canvas,
            PdfFont font,
            float fontSize,
            String text,
            float yPos) {
        addTaggedText(pdfDoc, page, parent, canvas, font, fontSize, text, PdfName.P, yPos);
    }

    private static void addTaggedText(
            PdfDocument pdfDoc,
            PdfPage page,
            PdfStructElem parent,
            PdfCanvas canvas,
            PdfFont font,
            float fontSize,
            String text,
            PdfName role,
            float yPos) {
        PdfStructElem elem = new PdfStructElem(pdfDoc, role, page);
        parent.addKid(elem);

        PdfMcrNumber mcr = new PdfMcrNumber(page, elem);
        elem.addKid(mcr);

        PdfDictionary props = new PdfDictionary();
        props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));
        canvas.beginMarkedContent(role, props);
        canvas.beginText();
        canvas.setFontAndSize(font, fontSize);
        canvas.moveText(72, yPos);
        canvas.showText(text);
        canvas.endText();
        canvas.endMarkedContent();
    }
}
