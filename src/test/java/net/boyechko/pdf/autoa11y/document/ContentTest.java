// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
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
