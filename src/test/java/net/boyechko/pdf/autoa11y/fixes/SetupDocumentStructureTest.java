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
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.ByteArrayOutputStream;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import org.junit.jupiter.api.Test;

class SetupDocumentStructureTest {

    @Test
    void createsDocumentWrapperWhenMissing() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            // Create structure with elements at root (no Document)
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            root.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();
            fix.apply(ctx);

            // Verify Document wrapper was created
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Root should have exactly one child (Document)");
            PdfStructElem docElem = (PdfStructElem) rootKids.get(0);
            assertEquals("Document", docElem.getRole().getValue());
        }
    }

    @Test
    void createsPartForEachPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();
            fix.apply(ctx);

            // Find Document element
            PdfStructElem docElem = (PdfStructElem) root.getKids().get(0);
            assertEquals("Document", docElem.getRole().getValue());

            // Count Part elements
            long partCount =
                    docElem.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .filter(e -> "Part".equals(e.getRole().getValue()))
                            .count();

            assertEquals(3, partCount, "Should have one Part per page");
        }
    }

    @Test
    void movesContentToCorrectPart() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();

            // Create elements with /Pg pointing to different pages
            PdfStructElem p1 = new PdfStructElem(pdfDoc, new PdfName("P"));
            p1.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(p1);

            PdfStructElem p2 = new PdfStructElem(pdfDoc, new PdfName("P"));
            p2.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            root.addKid(p2);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();
            fix.apply(ctx);

            // Find Document and Parts
            PdfStructElem docElem = (PdfStructElem) root.getKids().get(0);
            List<PdfStructElem> parts =
                    docElem.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .filter(e -> "Part".equals(e.getRole().getValue()))
                            .toList();

            assertEquals(2, parts.size(), "Should have 2 Parts");

            // Each Part should have one P child
            for (PdfStructElem part : parts) {
                List<IStructureNode> partKids = part.getKids();
                assertEquals(1, partKids.size(), "Each Part should have one child");
                PdfStructElem child = (PdfStructElem) partKids.get(0);
                assertEquals("P", child.getRole().getValue());
            }
        }
    }

    @Test
    void isIdempotent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            root.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();

            // Apply twice
            fix.apply(ctx);
            fix.apply(ctx);

            // Should still have just one Document with one Part
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size(), "Root should have exactly one child");

            PdfStructElem docElem = (PdfStructElem) rootKids.get(0);
            assertEquals("Document", docElem.getRole().getValue());

            long partCount =
                    docElem.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .filter(e -> "Part".equals(e.getRole().getValue()))
                            .count();
            assertEquals(1, partCount, "Should still have just one Part");
        }
    }

    @Test
    void preservesExistingDocument() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();

            // Create Document with existing content
            PdfStructElem docElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(docElem);
            PdfStructElem h1 = new PdfStructElem(pdfDoc, new PdfName("H1"));
            docElem.addKid(h1);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();
            fix.apply(ctx);

            // Document should still exist
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size());
            assertEquals("Document", ((PdfStructElem) rootKids.get(0)).getRole().getValue());
        }
    }

    @Test
    void findPartForPageReturnsCorrectPart() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();
            fix.apply(ctx);

            PdfStructElem docElem = (PdfStructElem) root.getKids().get(0);

            // Test static utility method
            PdfStructElem part1 = SetupDocumentStructure.findPartForPage(docElem, page1);
            PdfStructElem part2 = SetupDocumentStructure.findPartForPage(docElem, page2);

            assertNotNull(part1, "Should find Part for page 1");
            assertNotNull(part2, "Should find Part for page 2");
            assertNotSame(part1, part2, "Parts should be different");
        }
    }

    @Test
    void handlesEmptyStructureTree() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            // Don't add any structure elements - just empty root

            DocumentContext ctx = new DocumentContext(pdfDoc);
            SetupDocumentStructure fix = new SetupDocumentStructure();

            // Should not throw
            assertDoesNotThrow(() -> fix.apply(ctx));

            // Should still create Document and Part
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            List<IStructureNode> rootKids = root.getKids();
            assertEquals(1, rootKids.size());
            assertEquals("Document", ((PdfStructElem) rootKids.get(0)).getRole().getValue());
        }
    }
}
