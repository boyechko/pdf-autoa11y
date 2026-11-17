// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static net.boyechko.pdf.autoa11y.document.TreeNode.branch;
import static net.boyechko.pdf.autoa11y.document.TreeNode.leaf;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
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
    void requirePageOfReturnsTheMcrsPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            p.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            doc.getStructTreeRoot().addKid(p);
            PdfMcr mcr = p.addKid(new PdfMcrNumber(new PdfNumber(0), p));

            assertEquals(2, doc.getPageNumber(StructTree.requirePageOf(mcr)));
        }
    }

    @Test
    void requirePageOfRefusesUnresolvablePage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);
            PdfMcr orphan = new PdfMcrNumber(new PdfNumber(0), p);

            assertThrows(IllegalStateException.class, () -> StructTree.requirePageOf(orphan));
        }
    }

    @Test
    void kArrayAsArrayConvertsSingleChildEntry() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);

            assertNull(document.getPdfObject().getAsArray(PdfName.K));

            PdfArray normalized = StructTree.kArrayAsArray(document);
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
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P);
            document.addKid(p1);
            document.addKid(p2);

            PdfArray kArray = StructTree.kArrayAsArray(document);
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
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P);
            document.addKid(p1);
            document.addKid(p2);

            PdfStructElem orphan = new PdfStructElem(doc, PdfName.H1);

            PdfArray kArray = StructTree.kArrayAsArray(document);
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

            TreeNode<String> expected = branch("Document", leaf("H1"), branch("P", leaf("Span")));
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
        }
    }

    // --- isSame tests ---

    @Test
    void isSameMatchesSameDict() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);
            PdfObject dict = p.getPdfObject();

            assertTrue(StructTree.isSame(dict, dict));
        }
    }

    @Test
    void isSameMatchesDictAgainstItsIndirectRef() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);
            PdfObject dict = p.getPdfObject();
            PdfObject indRef = dict.getIndirectReference();
            assertNotNull(indRef, "struct elem should have an indirect reference");

            assertTrue(StructTree.isSame(dict, indRef), "dict vs indRef");
            assertTrue(StructTree.isSame(indRef, dict), "indRef vs dict (symmetry)");
        }
    }

    @Test
    void isSameMatchesSameIndirectRef() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);
            PdfObject indRef = p.getPdfObject().getIndirectReference();

            assertTrue(StructTree.isSame(indRef, indRef));
        }
    }

    @Test
    void isSameRejectsDifferentObjects() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P);
            root.addKid(p1);
            root.addKid(p2);

            assertFalse(StructTree.isSame(p1.getPdfObject(), p2.getPdfObject()));
            assertFalse(
                    StructTree.isSame(
                            p1.getPdfObject().getIndirectReference(),
                            p2.getPdfObject().getIndirectReference()));
            assertFalse(
                    StructTree.isSame(p1.getPdfObject(), p2.getPdfObject().getIndirectReference()));
        }
    }

    @Test
    void isSameMatchesKArrayEntryAgainstChildDict() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);

            // K array entries may be indirect refs or dicts — isSame must handle both
            PdfArray kArray = StructTree.kArrayAsArray(document);
            PdfObject kEntry = kArray.get(0);
            PdfObject childDict = p.getPdfObject();

            assertTrue(StructTree.isSame(kEntry, childDict));
            assertTrue(StructTree.isSame(childDict, kEntry));
        }
    }
}
