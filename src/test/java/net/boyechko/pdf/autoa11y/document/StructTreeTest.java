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
package net.boyechko.pdf.autoa11y.document;

import static net.boyechko.pdf.autoa11y.document.StructTree.Node.branch;
import static net.boyechko.pdf.autoa11y.document.StructTree.Node.leaf;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.StructTree.Node;
import org.junit.jupiter.api.Test;

class StructTreeTest extends PdfTestBase {

    /* iText's wrapper pattern: When you call getKids() on a structure node,
     * iText doesn't return the same Java objects you originally added. It
     * re-reads the underlying PDF dictionary and constructs fresh PdfStructElem
     * wrappers around each child's PdfDictionary. To compare the original
     * objects, use getPdfObject() instead of comparing the objects directly. */

    @Test
    void findFirstChildFindsMatchingRole() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(div);
            root.addKid(document);

            PdfStructElem found = StructTree.findFirstChild(root, PdfName.Document);
            assertNotNull(found);
            assertSame(document.getPdfObject(), found.getPdfObject());
        }
    }

    @Test
    void findFirstChildReturnsFirstMatch() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div1 = new PdfStructElem(doc, PdfName.Div);
            PdfStructElem div2 = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div1);
            root.addKid(div2);

            PdfStructElem found = StructTree.findFirstChild(root, PdfName.Div);
            assertSame(div1.getPdfObject(), found.getPdfObject());
        }
    }

    @Test
    void findFirstChildReturnsNullWhenNoMatch() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div);

            PdfStructElem found = StructTree.findFirstChild(root, PdfName.Document);
            assertNull(found);
        }
    }

    @Test
    void findFirstChildWorksWithPdfStructElemParent() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);

            PdfStructElem found = StructTree.findFirstChild(document, PdfName.Part);
            assertSame(part.getPdfObject(), found.getPdfObject());
        }
    }

    @Test
    void isSamePageMatchesSamePage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();

            assertTrue(StructTree.isSamePage(page.getPdfObject(), page));
        }
    }

    @Test
    void isSamePageRejectsDifferentPages() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            assertFalse(StructTree.isSamePage(page1.getPdfObject(), page2));
        }
    }

    @Test
    void determinePageNumberFromPgEntry() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            root.addKid(p);

            DocContext ctx = new DocContext(doc);
            assertEquals(1, StructTree.determinePageNumber(ctx, p));
        }
    }

    @Test
    void determinePageNumberFromChildRecursion() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div);
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            div.addKid(p);

            DocContext ctx = new DocContext(doc);
            // Div has no /Pg, but its child P does
            assertEquals(1, StructTree.determinePageNumber(ctx, div));
        }
    }

    @Test
    void determinePageNumberReturnsZeroWhenUnresolvable() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div);

            DocContext ctx = new DocContext(doc);
            assertEquals(0, StructTree.determinePageNumber(ctx, div));
        }
    }

    @Test
    void moveElementReparentsChild() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p);

            boolean moved = StructTree.moveElement(document, p, part);

            assertTrue(moved);
            // p should now be under part, not document
            List<IStructureNode> partKids = part.getKids();
            assertEquals(1, partKids.size());
            assertSame(p.getPdfObject(), ((PdfStructElem) partKids.get(0)).getPdfObject());
        }
    }

    @Test
    void moveElementWorkWithSingleChild() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            // document has exactly one child — /K is a direct reference, not an array
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            root.addKid(part);

            assertNull(document.getPdfObject().getAsArray(PdfName.K), "/K should not be an array");

            boolean moved = StructTree.moveElement(document, p, part);

            assertTrue(moved);
            List<IStructureNode> partKids = part.getKids();
            assertEquals(1, partKids.size());
            assertSame(p.getPdfObject(), ((PdfStructElem) partKids.get(0)).getPdfObject());
        }
    }

    @Test
    void moveElementReturnsFalseWhenNotFound() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);
            // orphan — not a child of document
            PdfStructElem orphan = new PdfStructElem(doc, new PdfName("P"));

            boolean moved = StructTree.moveElement(document, orphan, part);
            assertFalse(moved);
        }
    }

    @Test
    void removeFromParentWithStructElemParent() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p);

            StructTree.removeFromParent(p, document);

            List<IStructureNode> kids = document.getKids();
            assertTrue(kids == null || kids.isEmpty());
        }
    }

    @Test
    void removeFromParentWithTreeRoot() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div);

            StructTree.removeFromParent(div, root);

            List<IStructureNode> kids = root.getKids();
            assertTrue(kids == null || kids.isEmpty());
        }
    }

    @Test
    void getKArrayWorksWithStructElem() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p1 = new PdfStructElem(doc, new PdfName("P"));
            PdfStructElem p2 = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p1);
            document.addKid(p2);

            PdfArray kArray = StructTree.getKArray(document);
            assertNotNull(kArray);
            assertEquals(2, kArray.size());
        }
    }

    @Test
    void getKArrayWorksWithTreeRoot() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            PdfArray kArray = StructTree.getKArray(root);
            assertNotNull(kArray);
            assertTrue(kArray.size() > 0);
        }
    }

    @Test
    void getKArrayReturnsNullForSingleChildKEntry() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p);

            assertNull(document.getPdfObject().getAsArray(PdfName.K));
            assertNull(
                    StructTree.getKArray(document),
                    "Read-only getter should not normalize single-object /K");
        }
    }

    @Test
    void normalizeKArrayConvertsSingleChildKEntry() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p);

            assertNull(document.getPdfObject().getAsArray(PdfName.K));

            PdfArray normalized = StructTree.normalizeKArray(document);
            assertNotNull(normalized);
            assertEquals(1, normalized.size());
            assertEquals(0, StructTree.findIndexInKArray(normalized, p));
        }
    }

    @Test
    void findIndexInKArrayFindsElement() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p1 = new PdfStructElem(doc, new PdfName("P"));
            PdfStructElem p2 = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p1);
            document.addKid(p2);

            PdfArray kArray = StructTree.getKArray(document);
            assertEquals(0, StructTree.findIndexInKArray(kArray, p1));
            assertEquals(1, StructTree.findIndexInKArray(kArray, p2));
        }
    }

    @Test
    void findIndexInKArrayReturnsNegativeOneWhenMissing() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p1 = new PdfStructElem(doc, new PdfName("P"));
            PdfStructElem p2 = new PdfStructElem(doc, new PdfName("P"));
            document.addKid(p1);
            document.addKid(p2);

            PdfStructElem orphan = new PdfStructElem(doc, new PdfName("H1"));

            PdfArray kArray = StructTree.getKArray(document);
            assertNotNull(kArray);
            assertEquals(-1, StructTree.findIndexInKArray(kArray, orphan));
        }
    }

    @Test
    void toRoleTreeConvertsToRoleTree() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(doc, PdfName.H1);
            document.addKid(h1);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            p.addKid(span);

            Node<String> expected = branch("Document", leaf("H1"), branch("P", leaf("Span")));
            assertEquals(expected, StructTree.toRoleTree(document));
        }
    }

    @Test
    void getDescendantNavigatesToChild() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem h1 = new PdfStructElem(doc, PdfName.H1);
            document.addKid(h1);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            p.addKid(span);

            PdfStructElem descendant = (PdfStructElem) StructTree.getDescendant(root, 0, 1);
            assertSame(p.getPdfObject(), descendant.getPdfObject());
        }
    }

    @Test
    void pdfDocumentForReturnsDocument() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            assertEquals(doc, StructTree.pdfDocumentFor(document));
            assertEquals(doc, StructTree.pdfDocumentFor((IStructureNode) document));
        }
    }
}
