// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class AddParentInstructionTest extends PdfTestBase {

    @Test
    void addParentCreatesWrapper() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_PARENT Note[]").apply(ctx);

            PdfStructElem newParent = (PdfStructElem) p.getParent();
            assertEquals("Note", newParent.getRole().getValue());
            assertEquals("Document", ((PdfStructElem) newParent.getParent()).getRole().getValue());
        }
    }

    @Test
    void addParentPreservesPosition() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(h1);
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(target);
            PdfStructElem trailing = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(trailing);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(target, "!ADD_PARENT Note[]").apply(ctx);

            var kids = document.getKids();
            assertEquals(3, kids.size());
            assertEquals("H1", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("Note", ((PdfStructElem) kids.get(1)).getRole().getValue());
            assertEquals("P", ((PdfStructElem) kids.get(2)).getRole().getValue());
        }
    }

    @Test
    void addParentsBuildsNestedWrapperChain() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem span = new PdfStructElem(pdfDoc, PdfName.Span);
            document.addKid(span);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(span, "!ADD_PARENTS Reference[Link[P[]]]").apply(ctx);

            PdfStructElem p = (PdfStructElem) span.getParent();
            assertEquals("P", p.getRole().getValue());
            PdfStructElem link = (PdfStructElem) p.getParent();
            assertEquals("Link", link.getRole().getValue());
            PdfStructElem reference = (PdfStructElem) link.getParent();
            assertEquals("Reference", reference.getRole().getValue());
            assertEquals("Document", ((PdfStructElem) reference.getParent()).getRole().getValue());
        }
    }

    @Test
    void addParentRejectsBranchingChain() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem span = new PdfStructElem(pdfDoc, PdfName.Span);
            document.addKid(span);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ScribbledInstructionFix(span, "!ADD_PARENT Note[P[], Span[]]")
                                    .apply(ctx));
        }
    }

    @Test
    void addParentReceiptNamesTheChain() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_PARENT Note[]").apply(ctx);

            var scribble = StructTree.getScribble(p);
            assertNotNull(scribble);
            assertTrue(scribble.toolAuthored(), "Receipt should read as tool-authored");
            assertEquals(
                    ScribbledInstructionFix.INSTRUCTION_TAG + " ADD_PARENT Note[]",
                    scribble.body());
        }
    }
}
