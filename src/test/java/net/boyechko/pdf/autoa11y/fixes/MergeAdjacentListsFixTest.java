// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MergeAdjacentListsFixTest extends PdfTestBase {

    private static PdfStructElem listWithItems(
            PdfDocument pdfDoc, PdfStructElem parent, int items) {
        PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
        parent.addKid(list);
        for (int i = 0; i < items; i++) {
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);
        }
        return list;
    }

    @Test
    void mergesSecondListIntoAdjacentFirst() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = listWithItems(pdfDoc, document, 1);
            PdfStructElem second = listWithItems(pdfDoc, document, 2);

            new MergeAdjacentListsFix(first, second).apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[]], LI[LBody[]], LI[LBody[]]]]",
                    StructTree.toRoleTree(document).toString());
            DocValue.Scribble scribble = StructTree.getScribble(first);
            assertTrue(scribble.toolAuthored());
            assertEquals("3 items", scribble.body());
        }
    }

    @Test
    void refreshesItemCountWithoutClobberingUserScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = listWithItems(pdfDoc, document, 1);
            StructTree.setScribble(
                    first, "reviewed by hand" + StructTree.SCRIBBLE_SEPARATOR + "1 item");
            PdfStructElem second = listWithItems(pdfDoc, document, 1);

            new MergeAdjacentListsFix(first, second).apply(new DocContext(pdfDoc));

            DocValue.Scribble scribble = StructTree.getScribble(first);
            assertFalse(scribble.toolAuthored());
            assertEquals(List.of("reviewed by hand", "2 items"), scribble.segments());
        }
    }

    @Test
    void skipsWhenListsAreNotAdjacent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = listWithItems(pdfDoc, document, 1);
            PdfStructElem between = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(between);
            PdfStructElem second = listWithItems(pdfDoc, document, 1);

            new MergeAdjacentListsFix(first, second).apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[]]], P[], L[LI[LBody[]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }
}
