// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
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
            PdfStructElem item = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(item);
            item.addKid(new PdfStructElem(pdfDoc, PdfName.LBody));
        }
        return list;
    }

    @Test
    void stampsToolAuthoredEventAndCountOnUnscribbledList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.update(list, "event1");

            DocValue.Scribble scribble = StructTree.getScribble(list);
            assertTrue(scribble.toolAuthored());
            assertEquals(List.of("event1", ListItemScribble.countSegment(2)), scribble.segments());
        }
    }

    @Test
    void repeatedUpdateBySameFixLeavesOneEventAndOneCount() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.update(list, "event1");
            String afterFirst = StructTree.getScribble(list).value();
            ListItemScribble.update(list, "event1");

            assertEquals(afterFirst, StructTree.getScribble(list).value());
        }
    }

    @Test
    void secondFixAddsItsOwnEventAndRefreshesTheCount() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.update(list, "event1");
            list.addKid(new PdfStructElem(pdfDoc, PdfName.LI));
            ListItemScribble.update(list, "event2");

            DocValue.Scribble scribble = StructTree.getScribble(list);
            assertEquals(
                    List.of("event1", "event2", ListItemScribble.countSegment(3)),
                    scribble.segments());
        }
    }

    @Test
    void keepsUserSegmentsAndAuthorshipAcrossUpdates() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 3);
            StructTree.setScribble(list, "reviewed by hand");

            ListItemScribble.update(list, "event1");
            ListItemScribble.update(list, "event2");

            DocValue.Scribble scribble = StructTree.getScribble(list);
            assertFalse(scribble.toolAuthored());
            assertEquals(
                    List.of(
                            "reviewed by hand",
                            "event1",
                            "event2",
                            ListItemScribble.countSegment(3)),
                    scribble.segments());
        }
    }
}
