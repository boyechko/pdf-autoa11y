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
package net.boyechko.pdf.autoa11y.visitors;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.Base64;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class MissingAltTextVisitorTest extends PdfTestBase {
    private static final String ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0s0AAAAASUVORK5CYII=";

    @Test
    void detectsLargeFigureWithoutAltText() throws Exception {
        Path pdfFile = createTaggedImagePdf("large-no-alt.pdf", 200f, null);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MissingAltTextVisitor());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertEquals(1, issues.size(), "Large figure without alt text should be detected");
            assertEquals(IssueType.FIGURE_MISSING_ALT, issues.get(0).type());
            assertNull(issues.get(0).fix(), "Should have no automatic fix");
        }
    }

    @Test
    void doesNotFlagSmallFigure() throws Exception {
        Path pdfFile = createTaggedImagePdf("small-no-alt.pdf", 48f, null);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MissingAltTextVisitor());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertTrue(
                    issues.isEmpty(),
                    "Small figure should not be flagged (handled by MistaggedArtifactVisitor)");
        }
    }

    @Test
    void doesNotFlagFigureWithAltText() throws Exception {
        Path pdfFile = createTaggedImagePdf("large-with-alt.pdf", 200f, "A photo of a building");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MissingAltTextVisitor());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertTrue(issues.isEmpty(), "Figure with alt text should not be flagged");
        }
    }

    @Test
    void doesNotFlagFigureWithOnlyTextMcrs() throws Exception {
        // A figure with text content but no image MCR — FigureWithTextVisitor's territory
        Path pdfFile =
                createTestPdf(
                        testOutputPath("text-figure.pdf"),
                        (pdfDoc, layoutDoc) -> {
                            layoutDoc.add(
                                    new com.itextpdf.layout.element.Paragraph("Some text content"));
                        });
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new MissingAltTextVisitor());

            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertTrue(issues.isEmpty(), "Figure with only text MCRs should not be flagged");
        }
    }

    private Path createTaggedImagePdf(String filename, float renderedSize, String altText)
            throws Exception {
        return createStructuredTestPdf(
                testOutputPath(filename),
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem figure = new PdfStructElem(pdfDoc, PdfName.Figure, firstPage);
                    if (altText != null) {
                        figure.setAlt(new PdfString(altText));
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
