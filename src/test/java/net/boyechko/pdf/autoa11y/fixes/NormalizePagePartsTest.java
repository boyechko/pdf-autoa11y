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
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
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
            fix.apply(new DocumentContext(pdfDoc));

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
            fix.apply(new DocumentContext(pdfDoc));

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
            DocumentContext ctx = new DocumentContext(pdfDoc);
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
}
