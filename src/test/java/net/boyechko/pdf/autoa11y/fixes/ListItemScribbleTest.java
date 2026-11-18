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
import net.boyechko.pdf.autoa11y.document.DocValue.Scribble;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.fixes.ListItemScribble.Count;
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

    /** The segment that leads {@code list}'s scribble: its object number. */
    private static String numberOf(PdfStructElem list) {
        return String.valueOf(Tag.of(list).objNum());
    }

    // == The count segment =================================================

    @Test
    void countSegmentNamesItems() {
        assertEquals("items 4", Count.of(4).segment());
    }

    @Test
    void countSegmentCarriesMarkersAfterItems() {
        assertEquals("items 4, markers 1", Count.of(4, 1).segment());
    }

    @Test
    void countSegmentIsHeadedByItemsSoUnstampingReplacesIt() {
        Scribble scribble =
                Scribble.tool("MERGE lists" + Scribble.SEPARATOR + Count.of(2, 1).segment());

        assertEquals(List.of("MERGE lists"), scribble.without(Count.HEAD).segments());
    }

    // == Stamping a list ===================================================

    @Test
    void refreshLeadsWithTheListNumberAheadOfTheCounts() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 4);

            ListItemScribble.of(list).refresh(1);

            assertEquals(
                    List.of(numberOf(list), Count.of(4, 1).segment()),
                    Tag.of(list).scribble().segments());
        }
    }

    @Test
    void stampsToolAuthoredEventAndCountOnUnscribbledList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.of(list).record("event1");

            Scribble scribble = Tag.of(list).scribble();
            assertTrue(scribble.toolAuthored());
            assertEquals(
                    List.of(numberOf(list), "event1", Count.of(2).segment()), scribble.segments());
        }
    }

    @Test
    void repeatedUpdateBySameFixLeavesOneEventAndOneCount() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.of(list).record("event1");
            String afterFirst = Tag.of(list).scribble().value();
            ListItemScribble.of(list).record("event1");

            assertEquals(afterFirst, Tag.of(list).scribble().value());
        }
    }

    @Test
    void secondFixAddsItsOwnEventAndRefreshesTheCount() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 2);

            ListItemScribble.of(list).record("event1");
            list.addKid(new PdfStructElem(pdfDoc, PdfName.LI));
            ListItemScribble.of(list).record("event2");

            Scribble scribble = Tag.of(list).scribble();
            assertEquals(
                    List.of(numberOf(list), "event1", "event2", Count.of(3).segment()),
                    scribble.segments());
        }
    }

    @Test
    void keepsUserSegmentsAndAuthorshipAcrossUpdates() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructElem list = listWithItems(pdfDoc, 3);
            Tag.of(list).scribble(Scribble.user("reviewed by hand"));

            ListItemScribble.of(list).record("event1");
            ListItemScribble.of(list).record("event2");

            Scribble scribble = Tag.of(list).scribble();
            assertFalse(scribble.toolAuthored());
            assertEquals(
                    List.of(
                            numberOf(list),
                            "reviewed by hand",
                            "event1",
                            "event2",
                            Count.of(3).segment()),
                    scribble.segments());
        }
    }
}
