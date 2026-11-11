// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
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
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
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
            assertSame(pages.textLines(1), pages.textLines(1));
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

    @Test
    void contentAndLineBoundsAreReadPerMcr() throws Exception {
        Path pdfPath = testOutputPath();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfPath.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            pdfDoc.getStructTreeRoot().addKid(p);
            PdfMcr mcr = p.addKid(new PdfMcrDictionary(page, p));
            PdfCanvas canvas = new PdfCanvas(page);
            canvas.beginMarkedContent(PdfName.P, mcidProps(mcr.getMcid()));
            canvas.beginText()
                    .setFontAndSize(PdfFontFactory.createFont(StandardFonts.HELVETICA), 12)
                    .moveText(100, 700)
                    .showText("Hello")
                    .moveText(0, -14)
                    .showText("World")
                    .endText();
            canvas.endMarkedContent();
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PdfStructElem p = (PdfStructElem) pdfDoc.getStructTreeRoot().getKids().get(0);
            PdfMcr mcr = Tag.of(p).mcrs().get(0);
            PageContent pages = new PageContent(pdfDoc);

            assertTrue(pages.content(mcr).text().contains("Hello"));
            assertEquals(2, pages.lineBounds(mcr).size());
            assertEquals(2, pages.textLines(mcr).size());
        }
    }

    @Test
    void mcrWithoutResolvablePageHasNoContent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            pdfDoc.getStructTreeRoot().addKid(p);
            PdfMcr orphan = new PdfMcrNumber(new PdfNumber(0), p);

            PageContent pages = new PageContent(pdfDoc);

            assertNull(pages.content(orphan));
            assertNull(pages.bounds(orphan));
            assertTrue(pages.lineBounds(orphan).isEmpty());
            assertTrue(pages.textLines(orphan).isEmpty());
        }
    }

    @Test
    void elementBoundsOnPageIgnoreSameMcidOnOtherPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            pdfDoc.getStructTreeRoot().addKid(document);

            // Each page numbers its MCIDs from 0 in creation order, so creating this
            // unrelated P first on page 1 gives it MCID 0, near the bottom of the page
            PdfStructElem other = new PdfStructElem(pdfDoc, PdfName.P, page1);
            document.addKid(other);
            PdfMcr otherMcr = other.addKid(new PdfMcrNumber(page1, other));
            fillMarkedRect(page1, otherMcr, 100);

            // A P starting near the top of page 1 and continuing onto page 2, where it
            // reuses MCID 0
            PdfStructElem spanning = new PdfStructElem(pdfDoc, PdfName.P, page1);
            document.addKid(spanning);
            PdfMcr onPage1 = spanning.addKid(new PdfMcrNumber(page1, spanning));
            fillMarkedRect(page1, onPage1, 700);
            PdfMcr onPage2 = spanning.addKid(new PdfMcrDictionary(page2, spanning));
            fillMarkedRect(page2, onPage2, 700);
            assertEquals(otherMcr.getMcid(), onPage2.getMcid(), "precondition: MCIDs collide");

            PageContent pages = new PageContent(pdfDoc);
            Rectangle expected = pages.bounds(onPage1);
            Rectangle actual = pages.bounds(spanning, 1);

            assertTrue(
                    expected.equalsWithEpsilon(actual),
                    "expected " + expected + " but got " + actual);
        }
    }

    private static void fillMarkedRect(PdfPage page, PdfMcr mcr, float y) {
        PdfDictionary props = new PdfDictionary();
        props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));
        new PdfCanvas(page)
                .beginMarkedContent(PdfName.P, props)
                .rectangle(100, y, 200, 10)
                .fill()
                .endMarkedContent();
    }

    @Test
    void elementTextOnPageIncludesMcrDictionaries() throws Exception {
        Path pdfPath = testOutputPath();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfPath.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, page);
            pdfDoc.getStructTreeRoot().addKid(p);
            showMarkedText(page, p.addKid(new PdfMcrNumber(page, p)), "Hello", 700);
            showMarkedText(page, p.addKid(new PdfMcrDictionary(page, p)), "World", 680);
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PdfStructElem p = (PdfStructElem) pdfDoc.getStructTreeRoot().getKids().get(0);
            PageContent pages = new PageContent(pdfDoc);

            assertEquals("Hello World", pages.text(p, 1));
        }
    }

    @Test
    void elementTextOnPageIgnoresKidsOnOtherPages() throws Exception {
        Path pdfPath = testOutputPath();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfPath.toString()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, page1);
            pdfDoc.getStructTreeRoot().addKid(p);
            showMarkedText(page1, p.addKid(new PdfMcrNumber(page1, p)), "First", 700);
            // A Span continuing the P on page 2, where its MCID repeats page 1's
            PdfStructElem span = new PdfStructElem(pdfDoc, PdfName.Span, page2);
            p.addKid(span);
            showMarkedText(page2, span.addKid(new PdfMcrNumber(page2, span)), "Second", 700);
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PdfStructElem p = (PdfStructElem) pdfDoc.getStructTreeRoot().getKids().get(0);
            PageContent pages = new PageContent(pdfDoc);

            assertEquals("First", pages.text(p, 1));
            assertEquals("Second", pages.text(p, 2));
        }
    }

    /** Shows one Helvetica 12 line at (100, y) inside the MCR's marked-content block. */
    private static void showMarkedText(PdfPage page, PdfMcr mcr, String text, float y)
            throws Exception {
        new PdfCanvas(page)
                .beginMarkedContent(PdfName.P, mcidProps(mcr.getMcid()))
                .beginText()
                .setFontAndSize(PdfFontFactory.createFont(StandardFonts.HELVETICA), 12)
                .moveText(100, y)
                .showText(text)
                .endText()
                .endMarkedContent();
    }

    private static PdfDictionary mcidProps(int mcid) {
        PdfDictionary props = new PdfDictionary();
        props.put(PdfName.MCID, new PdfNumber(mcid));
        return props;
    }
}
