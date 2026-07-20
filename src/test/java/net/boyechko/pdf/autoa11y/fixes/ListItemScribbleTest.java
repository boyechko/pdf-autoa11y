// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class ListItemScribbleTest extends PdfTestBase {

    private static PdfStructElem listWithItems(PdfDocument pdfDoc, int items) {
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        pdfDoc.getStructTreeRoot().addKid(document);
        PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
        document.addKid(list);
        for (int i = 0; i < items; i++) {
            list.addKid(new PdfStructElem(pdfDoc, PdfName.LI));
        }
        return list;
    }

    @Test
    void stampsToolAuthoredScribbleOnUnscribbledList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.update(list);

            DocValue.Scribble scribble = StructTree.getScribble(list);
            assertTrue(scribble.toolAuthored());
            assertEquals(1, scribble.segments().size());
        }
    }

    @Test
    void repeatedUpdateReplacesCountSegmentInsteadOfAppending() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.update(list);
            String afterFirst = StructTree.getScribble(list).value();
            ListItemScribble.update(list);

            assertEquals(afterFirst, StructTree.getScribble(list).value());
        }
    }

    @Test
    void keepsUserSegmentsAndAuthorshipAcrossUpdates() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 3);
            StructTree.setScribble(list, "reviewed by hand");

            ListItemScribble.update(list);
            ListItemScribble.update(list);

            DocValue.Scribble scribble = StructTree.getScribble(list);
            assertFalse(scribble.toolAuthored());
            assertEquals(2, scribble.segments().size());
            assertEquals("reviewed by hand", scribble.segments().get(0));
        }
    }
}
