// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class StaleScribbleFixTest extends PdfTestBase {

    @Test
    void removesTitleWithScribblePrefix() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1);
            h1.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "NeedsReview"));
            document.addKid(h1);

            DocContext ctx = new DocContext(pdfDoc);
            new StaleScribbleFix(h1, SCRIBBLE_PREFIX + "NeedsReview").apply(ctx);

            assertNull(h1.getPdfObject().getAsString(PdfName.T));
        }
    }

    @Test
    void idempotentWhenTitleAlreadyAbsent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertDoesNotThrow(() -> new StaleScribbleFix(p, "__gone").apply(ctx));
            assertNull(p.getPdfObject().getAsString(PdfName.T));
        }
    }

    @Test
    void preservesOtherDictionaryEntries() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1);
            h1.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "OK"));
            h1.getPdfObject().put(PdfName.Lang, new PdfString("en"));
            document.addKid(h1);

            DocContext ctx = new DocContext(pdfDoc);
            new StaleScribbleFix(h1, SCRIBBLE_PREFIX + "OK").apply(ctx);

            assertNull(h1.getPdfObject().getAsString(PdfName.T));
            assertEquals("en", h1.getPdfObject().getAsString(PdfName.Lang).toUnicodeString());
        }
    }
}
