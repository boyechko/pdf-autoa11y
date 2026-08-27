// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static net.boyechko.pdf.autoa11y.document.StructTree.Node.branch;
import static net.boyechko.pdf.autoa11y.document.StructTree.Node.leaf;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
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
    void pageOfFromPgEntry() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            root.addKid(p);

            DocContext ctx = new DocContext(doc);
            assertEquals(1, StructTree.pageOf(p, ctx));
        }
    }

    @Test
    void pageOfFromChildRecursion() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            div.addKid(p);

            DocContext ctx = new DocContext(doc);
            // Div has no /Pg, but its child P does
            assertEquals(1, StructTree.pageOf(div, ctx));
        }
    }

    @Test
    void pageOfReturnsZeroWhenUnresolvable() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            root.addKid(div);

            DocContext ctx = new DocContext(doc);
            assertEquals(0, StructTree.pageOf(div, ctx));
        }
    }

    @Test
    void moveKidReparentsChild() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);

            StructTree.moveKid(p, document, part);

            List<IStructureNode> partKids = part.getKids();
            assertEquals(1, partKids.size());
            assertSame(p.getPdfObject(), ((PdfStructElem) partKids.get(0)).getPdfObject());
        }
    }

    @Test
    void moveKidWorksWithSingleChild() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            root.addKid(part);

            assertNull(document.getPdfObject().getAsArray(PdfName.K), "/K should not be an array");

            StructTree.moveKid(p, document, part);

            List<IStructureNode> partKids = part.getKids();
            assertEquals(1, partKids.size());
            assertSame(p.getPdfObject(), ((PdfStructElem) partKids.get(0)).getPdfObject());
        }
    }

    @Test
    void moveKidsGivesCrossPageMcrItsOwnPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            survivor.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            absorbed.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            root.addKid(absorbed);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(0), absorbed));

            StructTree.moveKids(absorbed, survivor);

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            // A bare number would now resolve through the survivor's page 1, so the MCR must
            // have been upgraded to a dictionary pinned to page 2.
            assertEquals(2, StructTree.pageOf((PdfMcr) moved));
            assertEquals(0, ((PdfMcr) moved).getMcid());
            assertNull(absorbed.getPdfObject().get(PdfName.K));
        }
    }

    @Test
    void moveKidsLeavesSamePageMcrAsBareNumber() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            survivor.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            root.addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            absorbed.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            root.addKid(absorbed);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(7), absorbed));

            StructTree.moveKids(absorbed, survivor);

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            assertTrue(((PdfMcr) moved).getPdfObject().isNumber(), "should stay a bare MCID");
            assertEquals(1, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void moveKidsKeepsPageOfMcrDictionary() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            survivor.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            absorbed.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(absorbed);
            PdfDictionary mcrDict = new PdfDictionary();
            mcrDict.put(PdfName.Type, PdfName.MCR);
            mcrDict.put(PdfName.Pg, page2.getPdfObject().getIndirectReference());
            mcrDict.put(PdfName.MCID, new PdfNumber(3));
            absorbed.addKid(new PdfMcrDictionary(mcrDict, absorbed));

            StructTree.moveKids(absorbed, survivor);

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            assertEquals(2, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void moveKidsHandlesInheritedParentPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            survivor.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(survivor);
            // The absorbed element has no /Pg of its own; it inherits page 2 from its parent.
            PdfStructElem section = new PdfStructElem(doc, PdfName.Sect);
            section.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            root.addKid(section);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            section.addKid(absorbed);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(0), absorbed));

            StructTree.moveKids(absorbed, survivor);

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            assertEquals(2, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void moveKidsAdoptsPageOntoPagelessDestination() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            // The survivor has no /Pg anywhere in its ancestry.
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            root.addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            absorbed.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            root.addKid(absorbed);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(0), absorbed));

            StructTree.moveKids(absorbed, survivor);

            // The destination adopts the page rather than every kid carrying its own /Pg.
            assertTrue(
                    StructTree.isSame(
                            survivor.getPdfObject().get(PdfName.Pg), page2.getPdfObject()));
            IStructureNode moved = survivor.getKids().get(0);
            assertTrue(((PdfMcr) moved).getPdfObject().isNumber(), "should stay a bare MCID");
            assertEquals(2, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void moveKidsKeepsDestinationsOwnKidsOnInheritedPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            // The survivor inherits page 1 from its section rather than carrying its own /Pg.
            PdfStructElem section = new PdfStructElem(doc, PdfName.Sect);
            section.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(section);
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            section.addKid(survivor);
            survivor.addKid(new PdfMcrNumber(new PdfNumber(5), survivor));

            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            absorbed.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            root.addKid(absorbed);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(0), absorbed));

            StructTree.moveKids(absorbed, survivor);

            // Without pinning the survivor to its inherited page first, iText would stamp the
            // incoming kid's page 2 onto it and drag the resident MCID 5 along.
            List<IStructureNode> kids = survivor.getKids();
            assertEquals(1, StructTree.pageOf(assertInstanceOf(PdfMcr.class, kids.get(0))));
            assertEquals(2, StructTree.pageOf(assertInstanceOf(PdfMcr.class, kids.get(1))));
        }
    }

    @Test
    void moveKidsPreservesOrderOfMixedKids() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P);
            survivor.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            root.addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P);
            absorbed.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            root.addKid(absorbed);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(1), absorbed));
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            absorbed.addKid(span);
            absorbed.addKid(new PdfMcrNumber(new PdfNumber(2), absorbed));

            StructTree.moveKids(absorbed, survivor);

            List<IStructureNode> kids = survivor.getKids();
            assertEquals(3, kids.size());
            assertEquals(1, assertInstanceOf(PdfMcr.class, kids.get(0)).getMcid());
            assertTrue(
                    StructTree.isSameElement(
                            assertInstanceOf(PdfStructElem.class, kids.get(1)), span));
            assertEquals(2, assertInstanceOf(PdfMcr.class, kids.get(2)).getMcid());
        }
    }

    @Test
    void moveKidRelocatesObjRefKeepingItsPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(0, 0, 10, 10));
            page2.addAnnotation(annot);

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.Link);
            survivor.getPdfObject().put(PdfName.Pg, page1.getPdfObject());
            root.addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.Link);
            absorbed.getPdfObject().put(PdfName.Pg, page2.getPdfObject());
            root.addKid(absorbed);
            PdfObjRef objRef = new PdfObjRef(annot, absorbed, doc.getNextStructParentIndex());
            absorbed.addKid(objRef);

            StructTree.moveKid(absorbed.getKids().get(0), absorbed, survivor);

            PdfObjRef moved = assertInstanceOf(PdfObjRef.class, survivor.getKids().get(0));
            assertEquals(2, StructTree.pageOf(moved));
            assertTrue(StructTree.isSame(moved.getReferencedObject(), annot.getPdfObject()));
        }
    }

    @Test
    void moveKidRejectsNodeThatIsNotAKidOfTheGivenParent() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);
            // The paragraph belongs to the part, not to the document.
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            part.addKid(p);

            assertThrows(
                    IllegalArgumentException.class, () -> StructTree.moveKid(p, document, part));
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
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
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

            Node<String> expected = branch("Document", leaf("H1"), branch("P", leaf("Span")));
            assertEquals(expected, StructTree.toRoleTree(document));
        }
    }

    @Test
    void parsesEmptyElement() {
        List<Node<String>> nodes = Node.fromString("Note[]");

        assertEquals(1, nodes.size());
        assertEquals("Note", nodes.get(0).value());
        assertTrue(nodes.get(0).children().isEmpty());
    }

    @Test
    void parsesNestedElements() {
        List<Node<String>> nodes = Node.fromString("Reference[Lbl[]]");

        assertEquals(1, nodes.size());
        assertEquals("Reference", nodes.get(0).value());
        assertEquals(1, nodes.get(0).children().size());
        assertEquals("Lbl", nodes.get(0).children().get(0).value());
    }

    @Test
    void parsesSiblingElements() {
        List<Node<String>> nodes = Node.fromString("Lbl[],LBody[]");

        assertEquals(2, nodes.size());
        assertEquals("Lbl", nodes.get(0).value());
        assertEquals("LBody", nodes.get(1).value());
    }

    @Test
    void parsesDeeplyNested() {
        List<Node<String>> nodes = Node.fromString("L[LI[Lbl[],LBody[P[]]]]");

        assertEquals(1, nodes.size());
        Node<String> l = nodes.get(0);
        assertEquals("L", l.value());
        Node<String> li = l.children().get(0);
        assertEquals("LI", li.value());
        assertEquals(2, li.children().size());
        assertEquals("Lbl", li.children().get(0).value());
        assertEquals("LBody", li.children().get(1).value());
        assertEquals("P", li.children().get(1).children().get(0).value());
    }

    @Test
    void roundTripsWithToString() {
        String[] expressions = {
            "Note[]", "Reference[Lbl[]]", "L[LI[Lbl[], LBody[P[]]]]",
        };
        for (String expr : expressions) {
            List<Node<String>> nodes = Node.fromString(expr);
            String result =
                    nodes.size() == 1
                            ? nodes.get(0).toString()
                            : String.join(", ", nodes.stream().map(Node::toString).toList());
            assertEquals(expr, result, "Round-trip failed for: " + expr);
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
    void okScribbleMarksElementVerified() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem art = new PdfStructElem(doc, PdfName.Art);
            doc.getStructTreeRoot().addKid(art);

            assertFalse(StructTree.isVerified(art), "no scribble means not verified");

            StructTree.setScribble(art, StructTree.SCRIBBLE_VERIFIED_TOKEN);
            assertTrue(StructTree.isVerified(art));
        }
    }

    @Test
    void okWithTrailingNoteDoesNotMarkElementVerified() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem art = new PdfStructElem(doc, PdfName.Art);
            doc.getStructTreeRoot().addKid(art);

            StructTree.setScribble(art, "OK reviewed 2026-07-09");
            assertFalse(StructTree.isVerified(art), "notes belong in their own segment");
        }
    }

    @Test
    void okInLaterScribbleSegmentMarksElementVerified() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem art = new PdfStructElem(doc, PdfName.Art);
            doc.getStructTreeRoot().addKid(art);

            StructTree.setScribble(art, "check spacing" + StructTree.SCRIBBLE_SEPARATOR + "OK");
            assertTrue(StructTree.isVerified(art));
        }
    }

    @Test
    void toolAuthoredOkScribbleDoesNotMarkElementVerified() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem art = new PdfStructElem(doc, PdfName.Art);
            doc.getStructTreeRoot().addKid(art);

            StructTree.setToolScribble(art, "OK");
            assertFalse(StructTree.isVerified(art), "verification is a user-only mark");
        }
    }

    @Test
    void okAsWordPrefixDoesNotMarkElementVerified() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructElem art = new PdfStructElem(doc, PdfName.Art);
            doc.getStructTreeRoot().addKid(art);

            StructTree.setScribble(art, "OKAY but check figures");
            assertFalse(StructTree.isVerified(art));
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
