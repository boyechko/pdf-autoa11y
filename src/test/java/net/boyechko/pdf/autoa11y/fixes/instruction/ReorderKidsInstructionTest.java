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

class ReorderKidsInstructionTest extends PdfTestBase {

    @Test
    void reorderKidsRespectsScribbledIndices() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            StructTree.setScribble(document, "!REORDER_KIDS");

            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(first);
            StructTree.setScribble(first, "first" + StructTree.SCRIBBLE_SEPARATOR + "REORDER 002");

            PdfStructElem second = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(second);
            StructTree.setScribble(
                    second, "second" + StructTree.SCRIBBLE_SEPARATOR + "REORDER 001");

            PdfStructElem third = new PdfStructElem(pdfDoc, PdfName.H2);
            document.addKid(third);
            StructTree.setScribble(third, "third" + StructTree.SCRIBBLE_SEPARATOR + "REORDER 003");

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(document, "!REORDER_KIDS").apply(ctx);

            var kids = document.getKids();
            assertEquals(3, kids.size());
            // Position 1 → "second" (P), 2 → "first" (H1), 3 → "third" (H2)
            assertEquals("P", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("H1", ((PdfStructElem) kids.get(1)).getRole().getValue());
            assertEquals("H2", ((PdfStructElem) kids.get(2)).getRole().getValue());
        }
    }

    @Test
    void reorderKidsClearsInstructionsAndPreservesIdentities() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            StructTree.setScribble(document, "!REORDER_KIDS");

            PdfStructElem kid = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(kid);
            StructTree.setScribble(kid, "alpha" + StructTree.SCRIBBLE_SEPARATOR + "REORDER 001");

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(document, "!REORDER_KIDS").apply(ctx);

            // Identity scribble preserved on kid; REORDER NNN cleared
            var kidScribble = StructTree.getScribble(kid);
            assertNotNull(kidScribble, "Kid's identity scribble should survive");
            assertEquals("alpha", kidScribble.value());

            // !REORDER_KIDS cleared on parent (no other segments → /T removed entirely)
            assertNull(StructTree.getScribble(document), "Parent's /T should be cleared");
        }
    }

    @Test
    void reorderKidsAppendsUnannotatedKidsAtEnd() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            StructTree.setScribble(document, "!REORDER_KIDS");

            // Mix annotated and unannotated kids; the unannotated ones should appear after
            // all annotated ones in their original relative order.
            PdfStructElem unann1 = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(unann1);

            PdfStructElem ann = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(ann);
            StructTree.setScribble(
                    ann, "annotated" + StructTree.SCRIBBLE_SEPARATOR + "REORDER 001");

            PdfStructElem unann2 = new PdfStructElem(pdfDoc, PdfName.H2);
            document.addKid(unann2);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(document, "!REORDER_KIDS").apply(ctx);

            var kids = document.getKids();
            assertEquals(3, kids.size());
            assertEquals("P", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("H1", ((PdfStructElem) kids.get(1)).getRole().getValue());
            assertEquals("H2", ((PdfStructElem) kids.get(2)).getRole().getValue());
        }
    }

    @Test
    void reorderReceiptNamesOldAndNewPosition() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            StructTree.setScribble(document, "!REORDER_KIDS");

            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(first);
            StructTree.setScribble(first, "REORDER 002");

            PdfStructElem second = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(second);
            StructTree.setScribble(second, "REORDER 001");

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(document, "!REORDER_KIDS").apply(ctx);

            var scribble = StructTree.getScribble(second);
            assertNotNull(scribble);
            assertTrue(scribble.toolAuthored(), "Receipt should read as tool-authored");
            assertEquals(
                    ScribbledInstructionFix.INSTRUCTION_TAG + " REORDER 2 -> 1", scribble.body());
        }
    }
}
