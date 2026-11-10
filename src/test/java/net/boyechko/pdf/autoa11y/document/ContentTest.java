// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.Test;

class ContentTest extends PdfTestBase {

    /** Draws the two-Bézier bullet circle at (x, y); filled discs also get a stroke pass. */
    private static void drawBulletCircle(PdfCanvas canvas, float x, float y, boolean filled) {
        canvas.saveState();
        canvas.concatMatrix(1, 0, 0, 1, x, y);
        canvas.moveTo(0, 0);
        canvas.curveTo(0, 2.5, -3.75, 2.5, -3.75, 0);
        canvas.curveTo(-3.75, -2.5, 0, -2.5, 0, 0);
        if (filled) {
            canvas.fill();
        } else {
            // Real converters close the stroked outline (`h S`), which iText
            // parses as a second, degenerate subpath
            canvas.closePath();
            canvas.stroke();
        }
        canvas.restoreState();
    }

    /** Fills a 200x10 rectangle at (100, y) inside a P block marked with the MCR's MCID. */
    /** Writes one MCID-0 block of Helvetica 12 lines, each a list of show-operator strings. */
    private static Path writeTextLines(Path pdfPath, List<List<String>> lines) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfPath.toString()))) {
            PdfDictionary props = new PdfDictionary();
            props.put(PdfName.MCID, new PdfNumber(0));
            PdfCanvas canvas = new PdfCanvas(pdfDoc.addNewPage());
            canvas.beginMarkedContent(PdfName.P, props)
                    .beginText()
                    .setFontAndSize(PdfFontFactory.createFont(StandardFonts.HELVETICA), 12);
            canvas.moveText(100, 700);
            for (List<String> chunks : lines) {
                chunks.forEach(canvas::showText);
                canvas.moveText(0, -14);
            }
            canvas.endText().endMarkedContent();
        }
        return pdfPath;
    }

    @Test
    void lineLeavesRoomOnlyWhenTheGapHoldsTheNextLinesFirstWord() {
        // Right edge at 372; the next line needs a 3pt space plus its 40pt first word.
        Content.TextLine line = new Content.TextLine(new Rectangle(72, 700, 300, 12), 40, 3, '.');
        Content.TextLine next = new Content.TextLine(new Rectangle(72, 686, 400, 12), 40, 3, 'x');

        assertTrue(line.leavesRoomFor(next, 472), "a 100pt gap holds the word");
        assertFalse(line.leavesRoomFor(next, 410), "a 38pt gap does not");
    }

    @Test
    void textLinesMeasureFirstWordAndSpace() throws Exception {
        PdfFont helvetica = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        Path pdfPath =
                writeTextLines(
                        testOutputPath(), List.of(List.of("Hello world"), List.of("Next line")));

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            List<Content.TextLine> lines =
                    Content.extractTextLinesForPage(pdfDoc.getPage(1)).get(0);

            assertEquals(2, lines.size());
            assertEquals(helvetica.getWidth("Hello", 12), lines.get(0).firstWordWidth(), 0.5f);
            assertEquals(helvetica.getWidth("Next", 12), lines.get(1).firstWordWidth(), 0.5f);
            assertEquals(helvetica.getWidth(" ", 12), lines.get(0).spaceWidth(), 0.5f);
        }
    }

    @Test
    void firstWordSpanningShowOperatorsIsMeasuredWhole() throws Exception {
        PdfFont helvetica = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        Path pdfPath = writeTextLines(testOutputPath(), List.of(List.of("Hel", "lo world")));

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            Content.TextLine line =
                    Content.extractTextLinesForPage(pdfDoc.getPage(1)).get(0).get(0);

            assertEquals(helvetica.getWidth("Hello", 12), line.firstWordWidth(), 0.5f);
        }
    }

    @Test
    void lineWithoutSpacesIsAllFirstWord() throws Exception {
        Path pdfPath = writeTextLines(testOutputPath(), List.of(List.of("Word")));

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            Content.TextLine line =
                    Content.extractTextLinesForPage(pdfDoc.getPage(1)).get(0).get(0);

            assertEquals(line.bounds().getWidth(), line.firstWordWidth(), 0.5f);
        }
    }

    @Test
    void textLinesNoteTheirLastVisibleCharacter() throws Exception {
        Path pdfPath =
                writeTextLines(
                        testOutputPath(), List.of(List.of("Hello world"), List.of("ends", ". ")));

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            List<Content.TextLine> lines =
                    Content.extractTextLinesForPage(pdfDoc.getPage(1)).get(0);

            assertEquals('d', lines.get(0).lastChar());
            assertEquals('.', lines.get(1).lastChar());
        }
    }

    @Test
    void detectsFilledAndHollowBullets() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();
            PdfCanvas canvas = new PdfCanvas(page);
            drawBulletCircle(canvas, 35.5f, 300f, true);
            drawBulletCircle(canvas, 35.5f, 300f, false); // stroke pass over the filled disc
            drawBulletCircle(canvas, 50.5f, 250f, false); // hollow (stroke-only) sub-bullet

            List<Content.BulletPosition> bullets = Content.extractBulletPositionsForPage(page);

            assertEquals(2, bullets.size(), "one filled + one hollow bullet: " + bullets);
            assertEquals(35.5f, bullets.get(0).x(), 1.0f);
            assertEquals(300f, bullets.get(0).y(), 1.0f);
            assertEquals(50.5f, bullets.get(1).x(), 1.0f);
            assertEquals(250f, bullets.get(1).y(), 1.0f);
        }
    }

    @Test
    void ignoresNonCirclePaths() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();
            PdfCanvas canvas = new PdfCanvas(page);
            canvas.rectangle(35.5, 300, 4, 5);
            canvas.stroke();
            canvas.moveTo(50, 250);
            canvas.lineTo(54, 255);
            canvas.stroke();

            assertTrue(Content.extractBulletPositionsForPage(page).isEmpty());
        }
    }
}
