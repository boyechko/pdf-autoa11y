// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class MissingDocumentFixTest extends PdfTestBase {

    @Test
    void createsDocumentWrapperWhenMissing() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            root.addKid(new PdfStructElem(pdfDoc, PdfName.Part));
            root.addKid(new PdfStructElem(pdfDoc, new PdfName("P")));

            MissingDocumentFix fix = new MissingDocumentFix();
            fix.apply(new DocContext(pdfDoc));

            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Root should contain only Document");
            assertTrue(rootKids.get(0) instanceof PdfStructElem);

            PdfStructElem document = (PdfStructElem) rootKids.get(0);
            assertEquals(PdfName.Document, document.getRole());
            assertEquals(
                    2, document.getKids().size(), "Document should wrap original root children");
        }
    }

    @Test
    void preservesExistingDocumentAndChildren() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            PdfStructElem heading = new PdfStructElem(pdfDoc, PdfName.H1);
            root.addKid(document);
            document.addKid(heading);

            MissingDocumentFix fix = new MissingDocumentFix();
            fix.apply(new DocContext(pdfDoc));

            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Existing Document should be kept");
            PdfStructElem remainingDocument = (PdfStructElem) rootKids.get(0);
            assertEquals(PdfName.Document, remainingDocument.getRole());
            assertEquals(1, remainingDocument.getKids().size(), "Existing children should be kept");
        }
    }

    @Test
    void isIdempotent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            root.addKid(new PdfStructElem(pdfDoc, PdfName.Part));

            MissingDocumentFix fix = new MissingDocumentFix();
            DocContext ctx = new DocContext(pdfDoc);
            fix.apply(ctx);
            fix.apply(ctx);

            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Should keep one Document at root");
            assertEquals(PdfName.Document, ((PdfStructElem) rootKids.get(0)).getRole());
        }
    }

    @Test
    void handlesEmptyStructureTree() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            MissingDocumentFix fix = new MissingDocumentFix();
            assertDoesNotThrow(() -> fix.apply(new DocContext(pdfDoc)));

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Should create Document for empty tree");
            assertEquals(PdfName.Document, ((PdfStructElem) rootKids.get(0)).getRole());
        }
    }
}
