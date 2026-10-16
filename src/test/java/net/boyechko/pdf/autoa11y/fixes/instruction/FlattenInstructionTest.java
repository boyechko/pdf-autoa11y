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
import org.junit.jupiter.api.Test;

class FlattenInstructionTest extends PdfTestBase {

    @Test
    void flattenHoistsNestedLeavesPreservingPosition() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem before = new PdfStructElem(pdfDoc, PdfName.H4);
            document.addKid(before);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            document.addKid(list);
            PdfStructElem after = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(after);

            PdfStructElem p1 = new PdfStructElem(pdfDoc, PdfName.P);
            PdfStructElem p2 = new PdfStructElem(pdfDoc, PdfName.P);
            for (PdfStructElem wrapped : new PdfStructElem[] {p1, p2}) {
                PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
                list.addKid(li);
                PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
                li.addKid(lBody);
                lBody.addKid(wrapped);
            }

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(document, "!FLATTEN").apply(ctx);

            var kids = document.getKids();
            assertEquals(4, kids.size(), "Document should hold H4, P1, P2, trailing P");
            assertEquals(before.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(p1.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
            assertEquals(p2.getPdfObject(), ((PdfStructElem) kids.get(2)).getPdfObject());
            assertEquals(after.getPdfObject(), ((PdfStructElem) kids.get(3)).getPdfObject());
            assertEquals(
                    document.getPdfObject(),
                    ((PdfStructElem) p1.getParent()).getPdfObject(),
                    "Hoisted element's parent pointer should be rebound to Document");
        }
    }

    @Test
    void flattenHoistsLeavesFromUnevenDepths() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            root.addKid(sect);

            PdfStructElem outer = new PdfStructElem(pdfDoc, PdfName.Div);
            sect.addKid(outer);
            PdfStructElem shallow = new PdfStructElem(pdfDoc, PdfName.P);
            outer.addKid(shallow);
            PdfStructElem inner = new PdfStructElem(pdfDoc, PdfName.Div);
            outer.addKid(inner);
            PdfStructElem deep = new PdfStructElem(pdfDoc, PdfName.P);
            inner.addKid(deep);
            PdfStructElem trailing = new PdfStructElem(pdfDoc, PdfName.P);
            sect.addKid(trailing);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(sect, "!FLATTEN").apply(ctx);

            var kids = sect.getKids();
            assertEquals(3, kids.size(), "Both Divs should be gone, leaving three paragraphs");
            assertEquals(shallow.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(deep.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
            assertEquals(trailing.getPdfObject(), ((PdfStructElem) kids.get(2)).getPdfObject());
        }
    }

    @Test
    void flattenOnAlreadyFlatElementChangesNothing() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            root.addKid(sect);
            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1);
            sect.addKid(h1);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            sect.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(sect, "!FLATTEN").apply(ctx);
            new ScribbledInstructionFix(sect, "!FLATTEN").apply(ctx);

            var kids = sect.getKids();
            assertEquals(2, kids.size(), "Flat children should stay put across repeated runs");
            assertEquals(h1.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(p.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
        }
    }

    @Test
    void flattenRefusesElementMixingContentWithNestedElements() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div, page);
            document.addKid(div);
            div.addKid(new PdfMcrNumber(page, div));
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, page);
            div.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(document, "!FLATTEN").apply(ctx));

            assertEquals(1, document.getKids().size(), "Div should remain in place");
            assertEquals(2, div.getKids().size(), "Div should keep its marked content and its P");
        }
    }

    @Test
    void flattenPinsPageOnHoistedElementLackingPg() throws Exception {
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
            new ScribbledInstructionFix(document, "!FLATTEN").apply(ctx);

            assertNotNull(
                    p.getPdfObject().get(PdfName.Pg),
                    "Hoisted P should have gained /Pg so its bare-int MCR still resolves");
            assertEquals(page.getPdfObject(), p.getPdfObject().get(PdfName.Pg));
        }
    }
}
