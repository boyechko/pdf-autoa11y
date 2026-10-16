// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.ListItemScribble;
import org.junit.jupiter.api.Test;

class UnlistInstructionTest extends PdfTestBase {

    @Test
    void unlistHoistsItemContentAndSplitsListAroundIt() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);

            PdfStructElem[] items = new PdfStructElem[3];
            PdfStructElem[] bodies = new PdfStructElem[3];
            for (int i = 0; i < items.length; i++) {
                items[i] = new PdfStructElem(pdfDoc, PdfName.LI);
                list.addKid(items[i]);
                bodies[i] = new PdfStructElem(pdfDoc, PdfName.LBody);
                items[i].addKid(bodies[i]);
            }
            bodies[0].addKid(new PdfStructElem(pdfDoc, PdfName.P));
            PdfStructElem heading = new PdfStructElem(pdfDoc, PdfName.H3);
            bodies[1].addKid(heading);
            PdfStructElem prose = new PdfStructElem(pdfDoc, PdfName.P);
            bodies[1].addKid(prose);
            bodies[2].addKid(new PdfStructElem(pdfDoc, PdfName.P));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(items[1], "!UNLIST").apply(ctx);

            var kids = div.getKids();
            assertEquals(4, kids.size(), "Div should hold L, H3, P, and a new L");
            assertEquals(list.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(heading.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
            assertEquals(prose.getPdfObject(), ((PdfStructElem) kids.get(2)).getPdfObject());
            PdfStructElem tail = (PdfStructElem) kids.get(3);
            assertEquals(PdfName.L, tail.getRole());

            assertEquals(1, list.getKids().size(), "Original list keeps the leading item");
            assertEquals(
                    items[0].getPdfObject(),
                    ((PdfStructElem) list.getKids().get(0)).getPdfObject());
            assertEquals(1, tail.getKids().size(), "New list takes the trailing item");
            assertEquals(
                    items[2].getPdfObject(),
                    ((PdfStructElem) tail.getKids().get(0)).getPdfObject());
            assertEquals(
                    div.getPdfObject(),
                    ((PdfStructElem) heading.getParent()).getPdfObject(),
                    "Hoisted element's parent pointer should be rebound to the list's parent");
        }
    }

    @Test
    void unlistOnConsecutiveItemsLeavesNoEmptyList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);

            PdfStructElem[] items = new PdfStructElem[4];
            for (int i = 0; i < items.length; i++) {
                items[i] = new PdfStructElem(pdfDoc, PdfName.LI);
                list.addKid(items[i]);
                PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
                items[i].addKid(lBody);
                lBody.addKid(new PdfStructElem(pdfDoc, PdfName.P));
            }

            // Fixes run in traversal order; the second resolves its list after the first split.
            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(items[1], "!UNLIST").apply(ctx);
            new ScribbledInstructionFix(items[2], "!UNLIST").apply(ctx);

            var roles = div.getKids().stream().map(k -> ((PdfStructElem) k).getRole()).toList();
            assertEquals(List.of(PdfName.L, PdfName.P, PdfName.P, PdfName.L), roles);
        }
    }

    @Test
    void unlistOnFirstItemKeepsOriginalListAfterHoistedContent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);

            PdfStructElem[] items = new PdfStructElem[2];
            for (int i = 0; i < items.length; i++) {
                items[i] = new PdfStructElem(pdfDoc, PdfName.LI);
                list.addKid(items[i]);
                PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
                items[i].addKid(lBody);
                lBody.addKid(new PdfStructElem(pdfDoc, PdfName.P));
            }

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(items[0], "!UNLIST").apply(ctx);

            var kids = div.getKids();
            assertEquals(2, kids.size(), "Div should hold P then the original L");
            assertEquals(PdfName.P, ((PdfStructElem) kids.get(0)).getRole());
            assertEquals(list.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
            assertEquals(1, list.getKids().size());
        }
    }

    @Test
    void unlistOnSoleItemRemovesList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);
            PdfStructElem heading = new PdfStructElem(pdfDoc, PdfName.H4);
            lBody.addKid(heading);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!UNLIST").apply(ctx);

            assertEquals(1, div.getKids().size(), "Emptied list should be gone");
            assertEquals(
                    heading.getPdfObject(), ((PdfStructElem) div.getKids().get(0)).getPdfObject());
        }
    }

    @Test
    void unlistRefreshesItemCountOnBothLists() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);

            PdfStructElem[] items = new PdfStructElem[4];
            for (int i = 0; i < items.length; i++) {
                items[i] = new PdfStructElem(pdfDoc, PdfName.LI);
                list.addKid(items[i]);
                PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
                items[i].addKid(lBody);
                lBody.addKid(new PdfStructElem(pdfDoc, PdfName.P));
            }

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(items[1], "!UNLIST").apply(ctx);

            PdfStructElem tail = (PdfStructElem) div.getKids().get(2);
            assertTrue(
                    StructTree.getScribble(list).body().contains(ListItemScribble.countSegment(1)));
            assertTrue(
                    StructTree.getScribble(tail).body().contains(ListItemScribble.countSegment(2)));
        }
    }

    @Test
    void unlistPinsPageOnHoistedElementLackingPg() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            document.addKid(list);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            // Only LBody carries /Pg; the wrapped P's bare-int MCR resolves through it.
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody, page);
            li.addKid(lBody);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            lBody.addKid(p);
            p.addKid(new PdfMcrNumber(page, p));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!UNLIST").apply(ctx);

            assertEquals(page.getPdfObject(), p.getPdfObject().get(PdfName.Pg));
        }
    }

    @Test
    void unlistRefusesElementThatIsNotListItem() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            div.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(p, "!UNLIST").apply(ctx));
        }
    }

    @Test
    void unlistRefusesItemWithLabelLeavingTreeUntouched() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            li.addKid(new PdfStructElem(pdfDoc, PdfName.Lbl));
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);
            lBody.addKid(new PdfStructElem(pdfDoc, PdfName.P));

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(li, "!UNLIST").apply(ctx));

            assertEquals(1, div.getKids().size(), "List should remain in place");
            assertEquals(2, li.getKids().size(), "Item should keep its Lbl and LBody");
        }
    }

    @Test
    void unlistRefusesBodyHoldingMarkedContentLeavingTreeUntouched() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody, page);
            li.addKid(lBody);
            lBody.addKid(new PdfMcrNumber(page, lBody));

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(li, "!UNLIST").apply(ctx));

            assertEquals(1, list.getKids().size(), "Item should remain in its list");
            assertEquals(1, lBody.getKids().size(), "LBody should keep its marked content");
        }
    }
}
