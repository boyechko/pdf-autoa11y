// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.lang.reflect.RecordComponent;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.Test;

class LocusTest extends PdfTestBase {

    @Test
    void variantsHoldNoPdfObjects() {
        for (Class<?> variant : Locus.class.getPermittedSubclasses()) {
            for (RecordComponent component : variant.getRecordComponents()) {
                assertFalse(
                        component.getType().getPackageName().startsWith("com.itextpdf"),
                        variant.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void objectLocationsRequireObjNum() {
        assertThrows(
                NullPointerException.class, () -> new Locus.AtObj(null, 1, Locus.ObjKind.GENERIC));
        assertThrows(NullPointerException.class, () -> new Locus.AtElem(null, 1, "P"));
    }

    @Test
    void directStructElemFallsBackToItsPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfPage second = pdfDoc.addNewPage();
            PdfStructElem direct = directStructElem();
            direct.getPdfObject().put(PdfName.Pg, second.getPdfObject());

            assertEquals(Locus.atPage(2), Locus.atElem(new DocContext(pdfDoc), direct));
        }
    }

    @Test
    void directStructElemWithoutPageIsDocumentWide() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            assertEquals(Locus.none(), Locus.atElem(new DocContext(pdfDoc), directStructElem()));
        }
    }

    /** A struct elem stored inline rather than as an indirect object, as in malformed files. */
    private static PdfStructElem directStructElem() {
        PdfDictionary dict = new PdfDictionary();
        dict.put(PdfName.Type, PdfName.StructElem);
        dict.put(PdfName.S, PdfName.P);
        return new PdfStructElem(dict);
    }
}
