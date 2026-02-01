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
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import org.junit.jupiter.api.Test;

class CreateLinkTagTest extends PdfTestBase {
    @Test
    void createsLinkTagWithObjRef() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            // create annotation without having iTextPDF auto-tag it
            page.addAnnotation(-1, linkAnnot, false);

            CreateLinkTag fix = new CreateLinkTag(linkAnnot.getPdfObject(), 1);
            fix.apply(new DocumentContext(pdfDoc));

            assertEquals(1, documentElem.getKids().size(), "Document should have one child (Link)");

            PdfStructElem linkElem = (PdfStructElem) documentElem.getKids().get(0);
            assertEquals(PdfName.Link, linkElem.getRole());

            List<IStructureNode> linkKids = linkElem.getKids();
            assertEquals(1, linkKids.size(), "Link should have one child (OBJR)");
            assertTrue(linkKids.get(0) instanceof PdfObjRef, "Child should be OBJR");
        }
    }

    @Test
    void createsLinkTagsOnMultiplePages() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            // Create basic Document structure manually (no SetupDocumentStructure dependency)
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);

            PdfLinkAnnotation linkAnnot1 =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://page1.com"));
            page1.addAnnotation(-1, linkAnnot1, false);

            PdfLinkAnnotation linkAnnot2 =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://page2.com"));
            page2.addAnnotation(-1, linkAnnot2, false);

            DocumentContext ctx = new DocumentContext(pdfDoc);

            new CreateLinkTag(linkAnnot1.getPdfObject(), 1).apply(ctx);
            new CreateLinkTag(linkAnnot2.getPdfObject(), 2).apply(ctx);

            assertEquals(
                    2, documentElem.getKids().size(), "Document should have two Link children");

            PdfStructElem link1 = (PdfStructElem) documentElem.getKids().get(0);
            PdfStructElem link2 = (PdfStructElem) documentElem.getKids().get(1);

            assertEquals(PdfName.Link, link1.getRole(), "First child should be Link");
            assertEquals(PdfName.Link, link2.getRole(), "Second child should be Link");

            assertEquals(1, link1.getKids().size(), "Link1 should have one OBJR child");
            assertEquals(1, link2.getKids().size(), "Link2 should have one OBJR child");

            assertTrue(link1.getKids().get(0) instanceof PdfObjRef, "Link1 child should be OBJR");
            assertTrue(link2.getKids().get(0) instanceof PdfObjRef, "Link2 child should be OBJR");
        }
    }

    @Test
    void setsStructParentOnAnnotation() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            assertFalse(
                    linkAnnot.getPdfObject().containsKey(PdfName.StructParent),
                    "Annotation should not have StructParent before fix");

            DocumentContext ctx = new DocumentContext(pdfDoc);

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);

            new CreateLinkTag(linkAnnot.getPdfObject(), 1).apply(ctx);

            assertTrue(
                    linkAnnot.getPdfObject().containsKey(PdfName.StructParent),
                    "Annotation should have StructParent after fix");
        }
    }

    @Test
    void handlesMultipleLinksOnSamePage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);

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

            new CreateLinkTag(linkAnnot1.getPdfObject(), 1).apply(ctx);
            new CreateLinkTag(linkAnnot2.getPdfObject(), 1).apply(ctx);
            new CreateLinkTag(linkAnnot3.getPdfObject(), 1).apply(ctx);

            assertEquals(3, documentElem.getKids().size(), "Document should have three Links");

            for (IStructureNode kid : documentElem.getKids()) {
                PdfStructElem linkElem = (PdfStructElem) kid;
                assertEquals(PdfName.Link, linkElem.getRole());
                assertEquals(1, linkElem.getKids().size(), "Each Link should have OBJR");
                assertTrue(linkElem.getKids().get(0) instanceof PdfObjRef);
            }
        }
    }

    @Test
    void throwsWhenDocumentElementMissing() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            DocumentContext ctx = new DocumentContext(pdfDoc);

            CreateLinkTag fix = new CreateLinkTag(linkAnnot.getPdfObject(), 1);
            assertThrows(IllegalStateException.class, () -> fix.apply(ctx));

            assertFalse(linkAnnot.getPdfObject().containsKey(PdfName.StructParent));
        }
    }

    @Test
    void throwsOnInvalidPageNum() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);

            CreateLinkTag fix = new CreateLinkTag(linkAnnot.getPdfObject(), 99);
            assertThrows(IndexOutOfBoundsException.class, () -> fix.apply(ctx));
        }
    }

    @Test
    void doesNotMatchContentFromOtherPages() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);

            // Create Part elements for each page (simulating SetupDocumentStructure)
            // Parts have /Pg attributes pointing to their respective pages
            PdfStructElem part1 = new PdfStructElem(pdfDoc, PdfName.Part, page1);
            PdfStructElem part2 = new PdfStructElem(pdfDoc, PdfName.Part, page2);
            documentElem.addKid(part1);
            documentElem.addKid(part2);

            // Add a paragraph on page 1 with /Pg pointing to page1
            // The /Pg attribute is what the fix uses to filter by page
            PdfStructElem para1 = new PdfStructElem(pdfDoc, PdfName.P, page1);
            part1.addKid(para1);

            // Create link annotation on page 2
            // Without page filtering in collectBounds(), para1 would be considered
            // as a potential parent even though it's on a different page
            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page2.addAnnotation(-1, linkAnnot, false);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            new CreateLinkTag(linkAnnot.getPdfObject(), 2).apply(ctx);

            // The key assertion: verify link is NOT nested under para1 from page 1
            // The fix's page filtering in collectBounds() prevents cross-page matches
            List<IStructureNode> para1Kids = para1.getKids();
            boolean linkUnderPara1 =
                    para1Kids != null
                            && para1Kids.stream()
                                    .anyMatch(
                                            kid ->
                                                    kid instanceof PdfStructElem elem
                                                            && PdfName.Link.equals(elem.getRole()));

            assertFalse(linkUnderPara1, "Link should NOT be under para1 from page 1");

            // Link should go under part2 or documentElem (both valid), just not under page 1
            // content
            boolean linkExists =
                    (part2.getKids() != null && !part2.getKids().isEmpty())
                            || documentElem.getKids().size() == 3; // part1, part2, link

            assertTrue(linkExists, "Link should exist somewhere in the structure tree");
        }
    }
}
