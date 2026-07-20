// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MistaggedHeadingFixTest extends PdfTestBase {

    @Test
    void marksElementWithToolAuthoredSetRoleInstruction() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            new MistaggedHeadingFix(p, PdfName.H3).apply(new DocContext(pdfDoc));

            // The fix proposes, rather than applies: the role stays P and a tool-authored
            // instruction is scribbled for a later ScribbledInstruction pass to execute.
            assertEquals(PdfName.P, p.getRole());
            DocValue.Scribble scribble = StructTree.getScribble(p);
            assertNotNull(scribble);
            assertTrue(scribble.toolAuthored(), "Marked instruction should be tool-authored");
            assertEquals("!SET_ROLE H3", scribble.body());
        }
    }
}
