// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.Test;

class PageContentTest extends PdfTestBase {

    @Test
    void boundsAreExtractedFromMarkedContent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();
            PdfCanvas canvas = new PdfCanvas(page);
            canvas.beginMarkedContent(PdfName.Figure, mcidProps(3));
            canvas.rectangle(100, 650, 50, 40);
            canvas.fill();
            canvas.endMarkedContent();

            PageContent pages = new PageContent(pdfDoc);

            assertNotNull(pages.bounds(1).get(3));
            assertNull(pages.bounds(1).get(4));
        }
    }

    @Test
    void repeatedLookupsReuseOneExtraction() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();
            PageContent pages = new PageContent(pdfDoc);

            assertSame(pages.bounds(1), pages.bounds(1));
            assertSame(pages.lineBounds(1), pages.lineBounds(1));
            assertSame(pages.content(1), pages.content(1));
            assertSame(pages.text(1), pages.text(1));
            assertSame(pages.bullets(1), pages.bullets(1));
        }
    }

    @Test
    void textIsReadPerMcid() throws Exception {
        Path pdfPath = testOutputPath();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfPath.toString()))) {
            PdfCanvas canvas = new PdfCanvas(pdfDoc.addNewPage());
            canvas.beginMarkedContent(PdfName.P, mcidProps(0));
            canvas.beginText()
                    .setFontAndSize(PdfFontFactory.createFont(StandardFonts.HELVETICA), 12)
                    .moveText(100, 700)
                    .showText("Hello")
                    .endText();
            canvas.endMarkedContent();
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PageContent pages = new PageContent(pdfDoc);

            assertEquals("Hello", pages.text(1, 0).strip());
            assertEquals("", pages.text(1, 1));
        }
    }

    private static PdfDictionary mcidProps(int mcid) {
        PdfDictionary props = new PdfDictionary();
        props.put(PdfName.MCID, new PdfNumber(mcid));
        return props;
    }
}
