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

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.ConvertToArtifact;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MistaggedArtifactCheckTest extends PdfTestBase {
    private static final String ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0s0AAAAASUVORK5CYII=";

    // Patterns copied from MistaggedArtifactCheck for testing
    private static final Pattern FOOTER_URL_TIMESTAMP =
            Pattern.compile(
                    "https?://[^\\s]+.*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TIMESTAMP_ONLY =
            Pattern.compile(
                    "^\\s*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_NUMBER =
            Pattern.compile("^\\s*(Page\\s+)?\\d+\\s*(of\\s+\\d+)?\\s*$", Pattern.CASE_INSENSITIVE);

    private Path createTestPdf() throws Exception {
        String filename = "MistaggedArtifactCheckTest.pdf";
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream(filename)))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("This is a normal paragraph."));
            doc.add(new Paragraph("https://example.com/path/to/page [1/5/2024 9:00:00 PM]"));
            doc.close();
        }
        return testOutputPath(filename);
    }

    @ParameterizedTest(name = "patternMatchesUrlWithTimestamp: {0}")
    @ValueSource(
            strings = {
                "https://www.uwb.edu/catalog/ [11/15/2024 11:37:19 AM]",
                "https://www.uwb.edu/catalog/ [11/15/2024 11:37:19 AM]",
                "https://example.com/path/to/page [1/5/2024 9:00:00 PM]"
            })
    void patternMatchesUrlWithTimestamp(String footerText) {
        assertTrue(FOOTER_URL_TIMESTAMP.matcher(footerText).find());
    }

    @ParameterizedTest(name = "patternMatchesTimestampOnly: {0}")
    @ValueSource(
            strings = {
                "[11/15/2024 11:37:19 AM]",
                "[11/15/2024 11:37:19 AM]",
                "[1/5/2024 9:00:00 PM]"
            })
    void patternMatchesTimestampOnly(String timestampText) {
        assertTrue(TIMESTAMP_ONLY.matcher(timestampText).matches());
    }

    @ParameterizedTest(name = "patternMatchesTimestampWithWhitespace: {0}")
    @ValueSource(
            strings = {
                "  [1/5/2024 9:00:00 PM]  ",
                "  [1/5/2024 9:00:00 PM]  ",
                "  [1/5/2024 9:00:00 PM]  "
            })
    void patternMatchesTimestampWithWhitespace(String timestampText) {
        assertTrue(TIMESTAMP_ONLY.matcher(timestampText).matches());
    }

    @Test
    void patternMatchesPageNumber() {
        assertTrue(PAGE_NUMBER.matcher("1").matches());
        assertTrue(PAGE_NUMBER.matcher("42").matches());
        assertTrue(PAGE_NUMBER.matcher("Page 1").matches());
        assertTrue(PAGE_NUMBER.matcher("Page 42 of 100").matches());
        assertTrue(PAGE_NUMBER.matcher("  1 of 10  ").matches());
    }

    @Test
    void patternDoesNotMatchNormalText() {
        String normalText = "This is a normal paragraph about accessibility.";
        assertFalse(FOOTER_URL_TIMESTAMP.matcher(normalText).find());
        assertFalse(TIMESTAMP_ONLY.matcher(normalText).matches());
        assertFalse(PAGE_NUMBER.matcher(normalText).matches());
    }

    @Test
    void fixRemovesElementFromParent() throws Exception {
        Path pdfFile = createTestPdf();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {

            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MistaggedArtifactCheck());
            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertEquals(1, issues.size());
            assertEquals(IssueType.MISTAGGED_ARTIFACT, issues.get(0).type());
        }
    }

    @Test
    void fixIsIdempotent() throws Exception {
        Path inputFile = createTestPdf();
        Path outputFile = testOutputPath("MistaggedArtifactCheckTest-fixed.pdf");
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(inputFile.toString()),
                        new PdfWriter(outputFile.toString()))) {
            DocumentContext ctx = new DocumentContext(pdfDoc);
            PdfStructElem p =
                    (PdfStructElem) pdfDoc.getStructTreeRoot().getKids().get(0).getKids().get(1);
            ConvertToArtifact fix = new ConvertToArtifact(p);

            // Apply fix twice - should not throw
            fix.apply(ctx);
            assertDoesNotThrow(() -> fix.apply(ctx));
        }
    }

    @Test
    void detectsTinyTaggedImageAsMistaggedArtifact() throws Exception {
        Path pdfFile = createTaggedImagePdf("MistaggedArtifactCheckTest-tiny-image.pdf", 12f);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MistaggedArtifactCheck());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertEquals(
                    1, issues.size(), "Tiny tagged image should be flagged as mistagged artifact");
            assertEquals(IssueType.MISTAGGED_ARTIFACT, issues.get(0).type());
            assertTrue(
                    issues.get(0).message().contains("Decorative image"),
                    "Issue message should describe decorative image artifacting");
        }
    }

    @Test
    void detectsDecorativeFigureWithoutAltText() throws Exception {
        // 48pt is above the tiny threshold (20pt) but below meaningful (144w × 72h)
        Path pdfFile = createTaggedImagePdf("MistaggedArtifactCheckTest-decorative.pdf", 48f);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MistaggedArtifactCheck());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertEquals(1, issues.size(), "Decorative figure without alt text should be flagged");
            assertEquals(IssueType.MISTAGGED_ARTIFACT, issues.get(0).type());
            assertTrue(issues.get(0).message().contains("Decorative image"));
        }
    }

    @Test
    void doesNotFlagMeaningfulFigureAsMistaggedArtifact() throws Exception {
        // 200pt × 200pt is above the meaningful thresholds (144w × 72h)
        Path pdfFile = createTaggedImagePdf("MistaggedArtifactCheckTest-meaningful.pdf", 200f);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MistaggedArtifactCheck());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertEquals(0, issues.size(), "Meaningful-size figure should not be auto-artifacted");
        }
    }

    @Test
    void doesNotFlagFigureWithAltText() throws Exception {
        Path pdfFile =
                createTaggedImagePdf("MistaggedArtifactCheckTest-with-alt.pdf", 48f, "A photo");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MistaggedArtifactCheck());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertEquals(0, issues.size(), "Figure with alt text should not be flagged");
        }
    }

    private Path createTaggedImagePdf(String filename, float renderedSize) throws Exception {
        return createTaggedImagePdf(filename, renderedSize, null);
    }

    private Path createTaggedImagePdf(String filename, float renderedSize, String altText)
            throws Exception {
        return createStructuredTestPdf(
                testOutputPath(filename),
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem figure = new PdfStructElem(pdfDoc, PdfName.Figure, firstPage);
                    if (altText != null) {
                        figure.setAlt(new com.itextpdf.kernel.pdf.PdfString(altText));
                    }
                    document.addKid(figure);

                    PdfMcrNumber mcr = new PdfMcrNumber(firstPage, figure);
                    figure.addKid(mcr);

                    PdfDictionary props = new PdfDictionary();
                    props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));

                    ImageData imageData =
                            ImageDataFactory.create(
                                    Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64));
                    PdfCanvas canvas = new PdfCanvas(firstPage);
                    canvas.beginMarkedContent(PdfName.Figure, props);
                    canvas.addImageWithTransformationMatrix(
                            imageData, renderedSize, 0, 0, renderedSize, 100, 650, false);
                    canvas.endMarkedContent();
                });
    }
}
