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

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class NormalizePagePartsTest extends PdfTestBase {

    @Test
    void createsPartsAndMovesSinglePageChildren() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(pdfDoc, PdfName.P, page1);
            PdfStructElem p2 = new PdfStructElem(pdfDoc, PdfName.P, page2);
            document.addKid(p1);
            document.addKid(p2);

            NormalizePageParts fix = new NormalizePageParts();
            fix.apply(new DocContext(pdfDoc));

            PdfStructElem part1 = NormalizePageParts.findPartForPage(document, page1);
            PdfStructElem part2 = NormalizePageParts.findPartForPage(document, page2);
            assertNotNull(part1, "Part for page 1 should exist");
            assertNotNull(part2, "Part for page 2 should exist");

            assertEquals(1, part1.getKids().size(), "Part for page 1 should contain one child");
            assertEquals(1, part2.getKids().size(), "Part for page 2 should contain one child");
            assertEquals(PdfName.P, ((PdfStructElem) part1.getKids().get(0)).getRole());
            assertEquals(PdfName.P, ((PdfStructElem) part2.getKids().get(0)).getRole());
        }
    }

    @Test
    void partElementsHavePgAndTitle() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            NormalizePageParts fix = new NormalizePageParts();
            fix.apply(new DocContext(pdfDoc));

            PdfStructElem part1 = NormalizePageParts.findPartForPage(document, page1);
            PdfStructElem part2 = NormalizePageParts.findPartForPage(document, page2);
            assertNotNull(part1, "Part for page 1 should exist");
            assertNotNull(part2, "Part for page 2 should exist");

            assertTrue(
                    StructTree.isSamePage(part1.getPdfObject().getAsDictionary(PdfName.Pg), page1),
                    "/Pg for part 1 should reference page 1");
            assertTrue(
                    StructTree.isSamePage(part2.getPdfObject().getAsDictionary(PdfName.Pg), page2),
                    "/Pg for part 2 should reference page 2");

            assertEquals("p. 1", part1.getPdfObject().getAsString(PdfName.T).getValue());
            assertEquals("p. 2", part2.getPdfObject().getAsString(PdfName.T).getValue());
        }
    }

    @Test
    void leavesUnmappedElementsUnderDocumentButStillCreatesPageParts() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            sect.addKid(new PdfStructElem(pdfDoc, PdfName.P));

            NormalizePageParts fix = new NormalizePageParts();
            fix.apply(new DocContext(pdfDoc));

            List<IStructureNode> docKids = document.getKids();
            assertEquals(
                    3, docKids.size(), "Document should retain Sect and add two Part elements");
            assertEquals(PdfName.Sect, ((PdfStructElem) docKids.get(0)).getRole());
            PdfStructElem part1 = NormalizePageParts.findPartForPage(document, page1);
            PdfStructElem part2 = NormalizePageParts.findPartForPage(document, page2);
            assertNotNull(part1, "Part for page 1 should still be created");
            assertNotNull(part2, "Part for page 2 should still be created");
            assertTrue(part1.getKids() == null || part1.getKids().isEmpty());
            assertTrue(part2.getKids() == null || part2.getKids().isEmpty());
        }
    }

    @Test
    void isIdempotent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            document.addKid(new PdfStructElem(pdfDoc, PdfName.H1, page1));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            NormalizePageParts fix = new NormalizePageParts();
            DocContext ctx = new DocContext(pdfDoc);
            fix.apply(ctx);
            fix.apply(ctx);

            PdfStructElem part1 = NormalizePageParts.findPartForPage(document, page1);
            PdfStructElem part2 = NormalizePageParts.findPartForPage(document, page2);
            assertNotNull(part1);
            assertNotNull(part2);
            assertEquals(1, part1.getKids().size(), "Page 1 Part should remain stable");
            assertEquals(1, part2.getKids().size(), "Page 2 Part should remain stable");
        }
    }

    /**
     * Pre-existing Part for one page mixed with loose elements: existing Part should be preserved
     * and loose elements should move to their page Parts.
     */
    @Test
    void mixOfExistingPartAndLooseElements() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            PdfPage page3 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // Page 1 already has a Part
            PdfStructElem existingPart = new PdfStructElem(pdfDoc, PdfName.Part, page1);
            existingPart.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            document.addKid(existingPart);
            existingPart.addKid(new PdfStructElem(pdfDoc, PdfName.H1, page1));

            // Loose elements for pages 2 and 3 (post-flattening)
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page3));

            NormalizePageParts fix = new NormalizePageParts();
            fix.apply(new DocContext(pdfDoc));

            // Existing Part for page 1 should still be there
            PdfStructElem part1 = NormalizePageParts.findPartForPage(document, page1);
            assertNotNull(part1, "Existing Part for page 1 should be preserved");
            assertEquals(
                    PdfName.H1,
                    ((PdfStructElem) part1.getKids().get(0)).getRole(),
                    "Original page 1 content should be intact");

            // Pages 2 and 3 should have their loose elements
            for (int pageNum = 2; pageNum <= 3; pageNum++) {
                PdfStructElem part =
                        NormalizePageParts.findPartForPage(document, pdfDoc.getPage(pageNum));
                assertNotNull(part, "Part should exist for page " + pageNum);
                int contentCount = countPageContent(part, pdfDoc, pageNum);
                assertTrue(
                        contentCount > 0,
                        "Part for page "
                                + pageNum
                                + " should have reachable content for that page");
            }
        }
    }

    /**
     * Interleaved flat elements from different pages should maintain their original relative
     * ordering within each Part.
     */
    @Test
    void readingOrderPreservedWithinPageParts() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // Interleaved: H1(p1), P(p2), P(p1), H1(p2)
            document.addKid(new PdfStructElem(pdfDoc, PdfName.H1, page1));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.H1, page2));

            NormalizePageParts fix = new NormalizePageParts();
            fix.apply(new DocContext(pdfDoc));

            PdfStructElem part1 = NormalizePageParts.findPartForPage(document, page1);
            List<IStructureNode> part1Kids = part1.getKids();
            assertEquals(2, part1Kids.size(), "Page 1 Part should have 2 children");
            assertEquals(
                    PdfName.H1,
                    ((PdfStructElem) part1Kids.get(0)).getRole(),
                    "First element on page 1 should be H1");
            assertEquals(
                    PdfName.P,
                    ((PdfStructElem) part1Kids.get(1)).getRole(),
                    "Second element on page 1 should be P");

            PdfStructElem part2 = NormalizePageParts.findPartForPage(document, page2);
            List<IStructureNode> part2Kids = part2.getKids();
            assertEquals(2, part2Kids.size(), "Page 2 Part should have 2 children");
            assertEquals(
                    PdfName.P,
                    ((PdfStructElem) part2Kids.get(0)).getRole(),
                    "First element on page 2 should be P");
            assertEquals(
                    PdfName.H1,
                    ((PdfStructElem) part2Kids.get(1)).getRole(),
                    "Second element on page 2 should be H1");
        }
    }

    /**
     * Counts non-Part structure elements within a subtree that carry a /Pg reference to the target
     * page. Part containers are excluded because they always carry /Pg but don't represent actual
     * document content.
     */
    private int countPageContent(PdfStructElem elem, PdfDocument doc, int targetPageNum) {
        int count = 0;
        if (!PdfName.Part.equals(elem.getRole())) {
            PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
            if (pg != null && doc.getPageNumber(pg) == targetPageNum) {
                count++;
            }
        }
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    count += countPageContent(childElem, doc, targetPageNum);
                }
            }
        }
        return count;
    }
}
