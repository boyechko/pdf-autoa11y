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
import net.boyechko.pdf.autoa11y.document.Tag;
import org.junit.jupiter.api.Test;

class SetActualTextInstructionTest extends PdfTestBase {

    @Test
    void setActualTextStoresPlainTextWithSpaces() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructElem span = spanInDocument(pdfDoc);

            new ScribbledInstructionFix(span, "!SET_ACTUAL_TEXT and so on")
                    .apply(new DocContext(pdfDoc));

            assertEquals("and so on", span.getActualText().toUnicodeString());
        }
    }

    @Test
    void setActualTextDecodesUnicodeEscapes() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructElem span = spanInDocument(pdfDoc);

            new ScribbledInstructionFix(span, "!SET_ACTUAL_TEXT C\\u2212")
                    .apply(new DocContext(pdfDoc));

            assertEquals("C−", span.getActualText().toUnicodeString());
        }
    }

    @Test
    void setActualTextReceiptKeepsEscapesAsciiSafe() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructElem span = spanInDocument(pdfDoc);

            new ScribbledInstructionFix(span, "!SET_ACTUAL_TEXT \\u2212")
                    .apply(new DocContext(pdfDoc));

            var scribble = Tag.of(span).scribble();
            assertTrue(scribble.toolAuthored(), "Receipt should read as tool-authored");
            assertEquals(
                    ScribbledInstructionFix.INSTRUCTION_TAG + " SET_ACTUAL_TEXT \\u2212",
                    scribble.body());
        }
    }

    private static PdfStructElem spanInDocument(PdfDocument pdfDoc) {
        PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        root.addKid(document);
        PdfStructElem span = new PdfStructElem(pdfDoc, PdfName.Span);
        document.addKid(span);
        return span;
    }
}
