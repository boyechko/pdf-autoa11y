/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

class SetupDocumentStructureTest extends PdfTestBase {

    @Test
    void createsDocumentWrapperWhenMissing() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            root.addKid(new PdfStructElem(pdfDoc, PdfName.Part));
            root.addKid(new PdfStructElem(pdfDoc, new PdfName("P")));

            SetupDocumentStructure fix = new SetupDocumentStructure();
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

            SetupDocumentStructure fix = new SetupDocumentStructure();
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

            SetupDocumentStructure fix = new SetupDocumentStructure();
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

            SetupDocumentStructure fix = new SetupDocumentStructure();
            assertDoesNotThrow(() -> fix.apply(new DocContext(pdfDoc)));

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Should create Document for empty tree");
            assertEquals(PdfName.Document, ((PdfStructElem) rootKids.get(0)).getRole());
        }
    }
}
