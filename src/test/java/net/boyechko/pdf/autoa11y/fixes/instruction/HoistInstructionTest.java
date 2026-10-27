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
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class HoistInstructionTest extends PdfTestBase {

    @Test
    void hoistReplacesSingleKidChainKeepingPosition() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem art = new PdfStructElem(pdfDoc, PdfName.Art);
            root.addKid(art);

            PdfStructElem heading = new PdfStructElem(pdfDoc, PdfName.H1);
            art.addKid(heading);
            PdfStructElem outerList = new PdfStructElem(pdfDoc, PdfName.L);
            art.addKid(outerList);
            PdfStructElem trailing = new PdfStructElem(pdfDoc, PdfName.P);
            art.addKid(trailing);

            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            outerList.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            lBody.addKid(p);
            PdfStructElem innerList = new PdfStructElem(pdfDoc, PdfName.L);
            p.addKid(innerList);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(innerList, "!HOIST").apply(ctx);

            var kids = art.getKids();
            assertEquals(3, kids.size(), "Inner list should take the outer list's place");
            assertEquals(heading.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(innerList.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
            assertEquals(trailing.getPdfObject(), ((PdfStructElem) kids.get(2)).getPdfObject());
            assertEquals(
                    art.getPdfObject(),
                    ((PdfStructElem) innerList.getParent()).getPdfObject(),
                    "Hoisted element's parent pointer should be rebound to Art");
            assertTrue(
                    StructTree.getScribble(innerList)
                            .body()
                            .contains(ScribbledInstruction.receipt("HOIST")));
        }
    }

    @Test
    void hoistStopsBelowFirstAncestorWithOtherKids() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            root.addKid(sect);
            PdfStructElem busy = new PdfStructElem(pdfDoc, PdfName.Div);
            sect.addKid(busy);
            PdfStructElem wrapper = new PdfStructElem(pdfDoc, PdfName.Div);
            busy.addKid(wrapper);
            PdfStructElem sibling = new PdfStructElem(pdfDoc, PdfName.P);
            busy.addKid(sibling);
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P);
            wrapper.addKid(target);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(target, "!HOIST").apply(ctx);

            assertEquals(1, sect.getKids().size(), "Div with other kids should stay in place");
            var kids = busy.getKids();
            assertEquals(2, kids.size(), "Only the single-kid wrapper should be collapsed");
            assertEquals(target.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(sibling.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
        }
    }

    @Test
    void hoistNeverReplacesTopLevelElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            document.addKid(div);
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P);
            div.addKid(target);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(target, "!HOIST").apply(ctx);

            var kids = document.getKids();
            assertEquals(1, kids.size(), "Document should survive, holding the hoisted P");
            assertEquals(target.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
        }
    }

    @Test
    void hoistRefusesWhenParentHoldsMarkedContentAlongside() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div, page);
            document.addKid(div);
            div.addKid(new PdfMcrNumber(page, div));
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P, page);
            div.addKid(target);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(target, "!HOIST").apply(ctx));

            assertEquals(1, document.getKids().size(), "Div should remain in place");
            assertEquals(2, div.getKids().size(), "Div should keep its marked content and its P");
        }
    }

    @Test
    void hoistPinsPageOnHoistedElementLackingPg() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            // Only the wrapper carries /Pg; the P's bare-int MCR resolves through it.
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div, page);
            document.addKid(div);
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P);
            div.addKid(target);
            target.addKid(new PdfMcrNumber(page, target));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(target, "!HOIST").apply(ctx);

            assertEquals(page.getPdfObject(), target.getPdfObject().get(PdfName.Pg));
        }
    }
}
