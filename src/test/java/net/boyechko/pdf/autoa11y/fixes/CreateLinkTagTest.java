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

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.ByteArrayOutputStream;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import org.junit.jupiter.api.Test;

class CreateLinkTagTest {

    @Test
    void createsLinkTagWithObjRef() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            // Create a Link annotation
            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            // Set up Document/Part structure first (CreateLinkTag depends on this)
            DocumentContext ctx = new DocumentContext(pdfDoc);
            new SetupDocumentStructure().apply(ctx);

            // Apply CreateLinkTag
            CreateLinkTag fix = new CreateLinkTag(linkAnnot.getPdfObject(), 1);
            fix.apply(ctx);

            // Find the Part for page 1
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem docElem = (PdfStructElem) root.getKids().get(0);
            PdfStructElem partElem = SetupDocumentStructure.findPartForPage(docElem, page);
            assertNotNull(partElem, "Part should exist for page");

            // Verify Link element was created under Part
            List<IStructureNode> partKids = partElem.getKids();
            assertEquals(1, partKids.size(), "Part should have one child (Link)");

            PdfStructElem linkElem = (PdfStructElem) partKids.get(0);
            assertEquals("Link", linkElem.getRole().getValue());

            // Verify OBJR was created as child of Link
            List<IStructureNode> linkKids = linkElem.getKids();
            assertEquals(1, linkKids.size(), "Link should have one child (OBJR)");
            assertTrue(linkKids.get(0) instanceof PdfObjRef, "Child should be OBJR");
        }
    }

    @Test
    void createsLinkInCorrectPart() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            // Create annotations on different pages
            PdfLinkAnnotation linkAnnot1 =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://page1.com"));
            page1.addAnnotation(-1, linkAnnot1, false);

            PdfLinkAnnotation linkAnnot2 =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://page2.com"));
            page2.addAnnotation(-1, linkAnnot2, false);

            // Set up Document/Part structure
            DocumentContext ctx = new DocumentContext(pdfDoc);
            new SetupDocumentStructure().apply(ctx);

            // Apply CreateLinkTag for each annotation
            new CreateLinkTag(linkAnnot1.getPdfObject(), 1).apply(ctx);
            new CreateLinkTag(linkAnnot2.getPdfObject(), 2).apply(ctx);

            // Verify links are in correct Parts
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem docElem = (PdfStructElem) root.getKids().get(0);

            PdfStructElem part1 = SetupDocumentStructure.findPartForPage(docElem, page1);
            PdfStructElem part2 = SetupDocumentStructure.findPartForPage(docElem, page2);

            assertEquals(1, part1.getKids().size(), "Part1 should have one Link");
            assertEquals(1, part2.getKids().size(), "Part2 should have one Link");

            assertEquals("Link", ((PdfStructElem) part1.getKids().get(0)).getRole().getValue());
            assertEquals("Link", ((PdfStructElem) part2.getKids().get(0)).getRole().getValue());
        }
    }

    @Test
    void setsStructParentOnAnnotation() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            // Verify no StructParent before fix
            assertFalse(
                    linkAnnot.getPdfObject().containsKey(PdfName.StructParent),
                    "Annotation should not have StructParent before fix");

            DocumentContext ctx = new DocumentContext(pdfDoc);
            new SetupDocumentStructure().apply(ctx);
            new CreateLinkTag(linkAnnot.getPdfObject(), 1).apply(ctx);

            // Verify StructParent was set
            assertTrue(
                    linkAnnot.getPdfObject().containsKey(PdfName.StructParent),
                    "Annotation should have StructParent after fix");
        }
    }

    @Test
    void handlesMultipleLinksOnSamePage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            // Create multiple annotations on same page
            PdfLinkAnnotation linkAnnot1 =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://link1.com"));
            PdfLinkAnnotation linkAnnot2 =
                    new PdfLinkAnnotation(new Rectangle(100, 150, 200, 20))
                            .setAction(PdfAction.createURI("https://link2.com"));
            PdfLinkAnnotation linkAnnot3 =
                    new PdfLinkAnnotation(new Rectangle(100, 200, 200, 20))
                            .setAction(PdfAction.createURI("https://link3.com"));

            page.addAnnotation(-1, linkAnnot1, false);
            page.addAnnotation(-1, linkAnnot2, false);
            page.addAnnotation(-1, linkAnnot3, false);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            new SetupDocumentStructure().apply(ctx);

            new CreateLinkTag(linkAnnot1.getPdfObject(), 1).apply(ctx);
            new CreateLinkTag(linkAnnot2.getPdfObject(), 1).apply(ctx);
            new CreateLinkTag(linkAnnot3.getPdfObject(), 1).apply(ctx);

            // Verify all three Links are in the same Part
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem docElem = (PdfStructElem) root.getKids().get(0);
            PdfStructElem partElem = SetupDocumentStructure.findPartForPage(docElem, page);

            assertEquals(3, partElem.getKids().size(), "Part should have three Links");

            for (IStructureNode kid : partElem.getKids()) {
                PdfStructElem linkElem = (PdfStructElem) kid;
                assertEquals("Link", linkElem.getRole().getValue());
                assertEquals(1, linkElem.getKids().size(), "Each Link should have OBJR");
                assertTrue(linkElem.getKids().get(0) instanceof PdfObjRef);
            }
        }
    }

    @Test
    void doesNothingWithoutPart() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            // Don't run SetupDocumentStructure - no Parts exist
            DocumentContext ctx = new DocumentContext(pdfDoc);

            // Should not throw, just log warning
            CreateLinkTag fix = new CreateLinkTag(linkAnnot.getPdfObject(), 1);
            assertDoesNotThrow(() -> fix.apply(ctx));

            // Annotation should still not have StructParent
            assertFalse(linkAnnot.getPdfObject().containsKey(PdfName.StructParent));
        }
    }

    @Test
    void throwsOnInvalidPageNum() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            new SetupDocumentStructure().apply(ctx);

            // Try to create link for page 99 (doesn't exist)
            CreateLinkTag fix = new CreateLinkTag(linkAnnot.getPdfObject(), 99);
            assertThrows(IndexOutOfBoundsException.class, () -> fix.apply(ctx));
        }
    }
}
