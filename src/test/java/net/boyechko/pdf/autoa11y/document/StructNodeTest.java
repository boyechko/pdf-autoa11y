// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
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
import org.junit.jupiter.api.Test;

class StructNodeTest extends PdfTestBase {

    @Test
    void kidsAreOfEveryKindInDocumentOrder() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(0, 0, 10, 10));
            page.addAnnotation(annot);
            PdfStructElem link = new PdfStructElem(doc, PdfName.Link, page);
            doc.getStructTreeRoot().addKid(link);
            link.addKid(new PdfMcrNumber(new PdfNumber(0), link));
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            link.addKid(span);
            link.addKid(new PdfObjRef(annot, link, doc.getNextStructParentIndex()));

            List<StructNode> kids = Tag.of(link).kids();

            assertEquals(3, kids.size());
            assertEquals(0, assertInstanceOf(MarkedContent.class, kids.get(0)).mcid());
            assertEquals(Tag.of(span), kids.get(1));
            assertInstanceOf(ObjectRef.class, kids.get(2));
        }
    }

    @Test
    void tagWithoutKidsHasAnEmptyKidList() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);

            assertEquals(List.of(), Tag.of(p).kids());
        }
    }

    @Test
    void everyKindOfKidNamesItsParentAndPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(0, 0, 10, 10));
            page2.addAnnotation(annot);
            PdfStructElem link = new PdfStructElem(doc, PdfName.Link, page2);
            doc.getStructTreeRoot().addKid(link);
            link.addKid(new PdfMcrDictionary(page2, link));
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span, page2);
            link.addKid(span);
            link.addKid(new PdfObjRef(annot, link, doc.getNextStructParentIndex()));

            for (StructNode kid : Tag.of(link).kids()) {
                assertEquals(Tag.of(link), kid.parent());
                assertEquals(2, kid.page());
            }
        }
    }

    @Test
    void markedContentReadTwiceFromTheTreeIsEqual() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(p);
            p.addKid(new PdfMcrNumber(new PdfNumber(0), p));
            p.addKid(new PdfMcrNumber(new PdfNumber(1), p));

            List<StructNode> once = Tag.of(p).kids();
            List<StructNode> again = Tag.of(p).kids();

            assertEquals(once, again);
            assertEquals(once.get(0).hashCode(), again.get(0).hashCode());
            assertNotEquals(once.get(0), once.get(1));
        }
    }

    @Test
    void movedMarkedContentLandsLastInItsNewParentOnTheSamePage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P, page1);
            doc.getStructTreeRoot().addKid(survivor);
            survivor.addKid(new PdfMcrNumber(page1, survivor));
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P, page2);
            doc.getStructTreeRoot().addKid(absorbed);
            PdfMcrNumber mcr = new PdfMcrNumber(page2, absorbed);
            absorbed.addKid(mcr);

            MarkedContent moved =
                    ((MarkedContent) Tag.of(absorbed).kids().get(0)).moveTo(Tag.of(survivor));

            assertEquals(List.of(), Tag.of(absorbed).kids());
            assertEquals(moved, Tag.of(survivor).kids().getLast());
            assertEquals(mcr.getMcid(), moved.mcid());
            assertEquals(2, moved.page());
        }
    }

    @Test
    void movedObjectRefKeepsItsPage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(0, 0, 10, 10));
            page2.addAnnotation(annot);
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.Link, page1);
            doc.getStructTreeRoot().addKid(survivor);
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.Link, page2);
            doc.getStructTreeRoot().addKid(absorbed);
            absorbed.addKid(new PdfObjRef(annot, absorbed, doc.getNextStructParentIndex()));

            ObjectRef moved = ((ObjectRef) Tag.of(absorbed).kids().get(0)).moveTo(Tag.of(survivor));

            assertEquals(List.of(moved), Tag.of(survivor).kids());
            assertEquals(2, moved.page());
        }
    }

    @Test
    void movedTagLandsLastInItsNewParent() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            doc.getStructTreeRoot().addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);
            part.addKid(new PdfStructElem(doc, PdfName.H1));

            Tag.of(p).moveTo(Tag.of(part));

            assertEquals(List.of(Tag.of(part)), Tag.of(document).kids());
            assertEquals(Tag.of(p), Tag.of(part).kids().getLast());
        }
    }

    @Test
    void movingATopLevelTagIsRefused() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            doc.getStructTreeRoot().addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            doc.getStructTreeRoot().addKid(part);

            assertThrows(IllegalStateException.class, () -> Tag.of(document).moveTo(Tag.of(part)));
        }
    }

    @Test
    void movingAllKidsPreservesTheirOrderAndEmptiesTheSource() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem survivor = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(survivor);
            survivor.addKid(new PdfMcrNumber(page, survivor));
            PdfStructElem absorbed = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(absorbed);
            PdfMcrNumber mcr = new PdfMcrNumber(page, absorbed);
            absorbed.addKid(mcr);
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            absorbed.addKid(span);

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

            List<StructNode> kids = Tag.of(survivor).kids();
            assertEquals(List.of(), Tag.of(absorbed).kids());
            assertEquals(3, kids.size());
            assertEquals(mcr.getMcid(), assertInstanceOf(MarkedContent.class, kids.get(1)).mcid());
            assertEquals(Tag.of(span), kids.get(2));
        }
    }

    @Test
    void reorderedKidsTakeTheGivenOrderOnTheSamePage() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(p);
            p.addKid(new PdfMcrNumber(page, p));
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            p.addKid(span);
            p.addKid(new PdfMcrNumber(page, p));
            List<StructNode> kids = Tag.of(p).kids();
            int firstMcid = ((MarkedContent) kids.get(0)).mcid();

            Tag.of(p).reorderKids(List.of(kids.get(1), kids.get(2), kids.get(0)));

            List<StructNode> reordered = Tag.of(p).kids();
            assertEquals(Tag.of(span), reordered.get(0));
            MarkedContent last = assertInstanceOf(MarkedContent.class, reordered.get(2));
            assertEquals(firstMcid, last.mcid());
            assertEquals(1, last.page());
        }
    }

    @Test
    void reorderingRefusesAListThatLeavesAKidOut() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            div.addKid(new PdfStructElem(doc, PdfName.P));
            div.addKid(new PdfStructElem(doc, PdfName.P));
            List<StructNode> kids = Tag.of(div).kids();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> Tag.of(div).reorderKids(List.of(kids.get(0), kids.get(0))));
            assertEquals(kids, Tag.of(div).kids());
        }
    }

    // == Moving between tags ================================================

    @Test
    void movedTagLeavesItsOldParentForTheNew() throws Exception {
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

            Tag.of(p).moveTo(Tag.of(part));

            List<IStructureNode> partKids = part.getKids();
            assertEquals(1, partKids.size());
            assertSame(p.getPdfObject(), ((PdfStructElem) partKids.get(0)).getPdfObject());
        }
    }

    @Test
    void tagMovesOutOfAParentWhoseKIsNotAnArray() throws Exception {
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

            Tag.of(p).moveTo(Tag.of(part));

            List<IStructureNode> partKids = part.getKids();
            assertEquals(1, partKids.size());
            assertSame(p.getPdfObject(), ((PdfStructElem) partKids.get(0)).getPdfObject());
        }
    }

    @Test
    void markedContentMovedAcrossPagesCarriesItsOwnPage() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

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
    void markedContentMovedOnItsPageStaysABareNumber() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            assertTrue(((PdfMcr) moved).getPdfObject().isNumber(), "should stay a bare MCID");
            assertEquals(1, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void markedContentDictionaryKeepsItsPageWhenMoved() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            assertEquals(2, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void markedContentOnAnInheritedPageKeepsItWhenMoved() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

            IStructureNode moved = survivor.getKids().get(0);
            assertInstanceOf(PdfMcr.class, moved);
            assertEquals(2, StructTree.pageOf((PdfMcr) moved));
        }
    }

    @Test
    void pagelessDestinationTakesThePageOfMovedContent() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

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
    void destinationKidsKeepTheirInheritedPage() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

            // Without pinning the survivor to its inherited page first, iText would stamp the
            // incoming kid's page 2 onto it and drag the resident MCID 5 along.
            List<IStructureNode> kids = survivor.getKids();
            assertEquals(1, StructTree.pageOf(assertInstanceOf(PdfMcr.class, kids.get(0))));
            assertEquals(2, StructTree.pageOf(assertInstanceOf(PdfMcr.class, kids.get(1))));
        }
    }

    @Test
    void movingMixedKidsPreservesTheirOrder() throws Exception {
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

            Tag.of(absorbed).moveKidsTo(Tag.of(survivor));

            List<IStructureNode> kids = survivor.getKids();
            assertEquals(3, kids.size());
            assertEquals(1, assertInstanceOf(PdfMcr.class, kids.get(0)).getMcid());
            assertTrue(
                    Tag.of(assertInstanceOf(PdfStructElem.class, kids.get(1)))
                            .equals(Tag.of(span)));
            assertEquals(2, assertInstanceOf(PdfMcr.class, kids.get(2)).getMcid());
        }
    }

    @Test
    void movedObjectRefKeepsItsPageAndTarget() throws Exception {
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

            Tag.of(absorbed).kids().get(0).moveTo(Tag.of(survivor));

            PdfObjRef moved = assertInstanceOf(PdfObjRef.class, survivor.getKids().get(0));
            assertEquals(2, StructTree.pageOf(moved));
            assertTrue(StructTree.isSame(moved.getReferencedObject(), annot.getPdfObject()));
        }
    }

    @Test
    void movingANodeItsParentDoesNotListIsRefused() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem part = new PdfStructElem(doc, PdfName.Part);
            document.addKid(part);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            part.addKid(p);
            // /P still names the part, but the part no longer lists the paragraph.
            part.getPdfObject().remove(PdfName.K);

            assertThrows(IllegalArgumentException.class, () -> Tag.of(p).moveTo(Tag.of(document)));
        }
    }
}
