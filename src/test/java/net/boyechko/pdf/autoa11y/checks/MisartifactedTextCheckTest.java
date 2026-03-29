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
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class MisartifactedTextCheckTest extends PdfTestBase {

    @Test
    void detectsDigitOnlyArtifactText() throws Exception {
        Path pdfFile = createPdfWithArtifactDigit("detectsDigitOnlyArtifactText.pdf", "42");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MisartifactedTextCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(1, issues.size());
            assertEquals(IssueType.MISARTIFACTED_TEXT, issues.get(0).type());
            assertNotNull(issues.get(0).fix(), "Should have a fix");
        }
    }

    @Test
    void ignoresNonDigitArtifactText() throws Exception {
        Path pdfFile = createPdfWithArtifactDigit("ignoresNonDigitArtifactText.pdf", "Header");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MisartifactedTextCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(0, issues.size());
        }
    }

    @Test
    void ignoresTaggedDigitText() throws Exception {
        // A digit that is properly tagged (not artifacted) should not be flagged
        createStructuredTestPdf(
                testOutputPath("ignoresTaggedDigitText.pdf"),
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    document.addKid(p);
                    PdfMcrNumber mcr = new PdfMcrNumber(firstPage, p);
                    p.addKid(mcr);

                    PdfDictionary props = new PdfDictionary();
                    props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));

                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    PdfCanvas canvas = new PdfCanvas(firstPage);
                    canvas.beginMarkedContent(PdfName.P, props);
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 700)
                            .showText("42")
                            .endText();
                    canvas.endMarkedContent();
                });

        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(testOutputPath("ignoresTaggedDigitText.pdf").toString()))) {
            IssueList issues = new MisartifactedTextCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(0, issues.size());
        }
    }

    @Test
    void detectsMultipleArtifactDigitsOnSamePage() throws Exception {
        Path pdfFile =
                createStructuredTestPdf(
                        testOutputPath("detectsMultipleArtifactDigitsOnSamePage.pdf"),
                        (pdfDoc, firstPage, root, document) -> {
                            // Tagged paragraph for context
                            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                            document.addKid(p);
                            PdfMcrNumber mcr = new PdfMcrNumber(firstPage, p);
                            p.addKid(mcr);

                            PdfDictionary props = new PdfDictionary();
                            props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));

                            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                            PdfCanvas canvas = new PdfCanvas(firstPage);

                            // First artifact digit
                            canvas.beginMarkedContent(new PdfName("Artifact"));
                            canvas.beginText()
                                    .setFontAndSize(font, 10)
                                    .moveText(100, 750)
                                    .showText("1")
                                    .endText();
                            canvas.endMarkedContent();

                            // Tagged content
                            canvas.beginMarkedContent(PdfName.P, props);
                            canvas.beginText()
                                    .setFontAndSize(font, 10)
                                    .moveText(100, 700)
                                    .showText("Some body text")
                                    .endText();
                            canvas.endMarkedContent();

                            // Second artifact digit
                            canvas.beginMarkedContent(new PdfName("Artifact"));
                            canvas.beginText()
                                    .setFontAndSize(font, 10)
                                    .moveText(100, 650)
                                    .showText("2")
                                    .endText();
                            canvas.endMarkedContent();
                        });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new MisartifactedTextCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(2, issues.size());
        }
    }

    /** Creates a PDF with one tagged paragraph and one artifact block containing the given text. */
    private Path createPdfWithArtifactDigit(String filename, String artifactText) throws Exception {
        return createStructuredTestPdf(
                testOutputPath(filename),
                (pdfDoc, firstPage, root, document) -> {
                    // Tagged paragraph for neighbor MCID context
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    document.addKid(p);
                    PdfMcrNumber mcr = new PdfMcrNumber(firstPage, p);
                    p.addKid(mcr);

                    PdfDictionary props = new PdfDictionary();
                    props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));

                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    PdfCanvas canvas = new PdfCanvas(firstPage);

                    // Artifact block with the specified text
                    canvas.beginMarkedContent(new PdfName("Artifact"));
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 750)
                            .showText(artifactText)
                            .endText();
                    canvas.endMarkedContent();

                    // Tagged content (neighbor)
                    canvas.beginMarkedContent(PdfName.P, props);
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 700)
                            .showText("Some body text")
                            .endText();
                    canvas.endMarkedContent();
                });
    }
}
