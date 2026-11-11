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
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.Test;

class TagTest extends PdfTestBase {

    @Test
    void objNumIsTheElementsObjectNumber() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);

            int expected = p.getPdfObject().getIndirectReference().getObjNumber();
            assertEquals(expected, Tag.of(p).objNum());
        }
    }

    @Test
    void roleIsMappedThroughTheRoleMap() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructTreeRoot root = doc.getStructTreeRoot();
            root.addRoleMapping("Bullet", "LI");
            PdfStructElem item = new PdfStructElem(doc, new PdfName("Bullet"));
            root.addKid(item);

            assertEquals("LI", Tag.of(item).role());
        }
    }

    @Test
    void roleIsTheRawRoleWhenUnmapped() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);

            assertEquals("P", Tag.of(p).role());
        }
    }

    @Test
    void parentIsTheEnclosingElement() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            div.addKid(p);

            assertEquals(Tag.of(div), Tag.of(p).parent());
        }
    }

    @Test
    void parentIsNullDirectlyUnderTheStructTreeRoot() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            doc.getStructTreeRoot().addKid(document);

            assertNull(Tag.of(document).parent());
        }
    }

    @Test
    void closestIsTheElementItselfWhenItHasTheRole() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem item = new PdfStructElem(doc, PdfName.LI);
            doc.getStructTreeRoot().addKid(item);

            assertEquals(Tag.of(item), Tag.of(item).closest("LI"));
        }
    }

    @Test
    void closestIsTheNearestAncestorWithOneOfTheMappedRoles() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructTreeRoot root = doc.getStructTreeRoot();
            root.addRoleMapping("Bullet", "LI");
            PdfStructElem list = new PdfStructElem(doc, PdfName.L);
            root.addKid(list);
            PdfStructElem item = new PdfStructElem(doc, new PdfName("Bullet"));
            list.addKid(item);
            PdfStructElem para = new PdfStructElem(doc, PdfName.P);
            item.addKid(para);

            assertEquals(Tag.of(item), Tag.of(para).closest("L", "LI"));
        }
    }

    @Test
    void closestIsNullWhenNoElementHasTheRole() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            PdfStructElem para = new PdfStructElem(doc, PdfName.P);
            div.addKid(para);

            assertNull(Tag.of(para).closest("L"));
        }
    }

    @Test
    void isDescendantOfHoldsForAnyAncestorButNotTheElementItself() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            div.addKid(p);
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            p.addKid(span);

            assertTrue(Tag.of(span).isDescendantOf(Tag.of(div)));
            assertFalse(Tag.of(div).isDescendantOf(Tag.of(span)));
            assertFalse(Tag.of(span).isDescendantOf(Tag.of(span)));
        }
    }

    @Test
    void commonAncestorIsTheDeepestElementHoldingAllOfThem() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            PdfStructElem list = new PdfStructElem(doc, PdfName.L);
            div.addKid(list);
            PdfStructElem first = new PdfStructElem(doc, PdfName.LI);
            list.addKid(first);
            PdfStructElem second = new PdfStructElem(doc, PdfName.LI);
            list.addKid(second);

            assertEquals(
                    Tag.of(list), Tag.commonAncestorOf(List.of(Tag.of(first), Tag.of(second))));
            assertEquals(Tag.of(list), Tag.commonAncestorOf(List.of(Tag.of(first), Tag.of(list))));
        }
    }

    @Test
    void childrenAreTheElementKidsOnly() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(p);
            p.addKid(new PdfMcrNumber(new PdfNumber(0), p));
            PdfStructElem span = new PdfStructElem(doc, PdfName.Span);
            p.addKid(span);

            assertEquals(List.of(Tag.of(span)), Tag.of(p).children());
        }
    }

    @Test
    void mcrsAreTheMarkedContentKidsWithoutObjRefs() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(0, 0, 10, 10));
            page.addAnnotation(annot);
            PdfStructElem link = new PdfStructElem(doc, PdfName.Link, page);
            doc.getStructTreeRoot().addKid(link);
            link.addKid(new PdfMcrNumber(new PdfNumber(0), link));
            link.addKid(new PdfObjRef(annot, link, doc.getNextStructParentIndex()));

            Tag tag = Tag.of(link);
            assertEquals(1, tag.mcrs().size());
            assertEquals(0, tag.mcrs().get(0).getMcid());
            assertEquals(1, tag.objRefs().size());
        }
    }

    @Test
    void holdsOnlyMarkedContentWhenEveryKidIsAnMcr() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(p);
            p.addKid(new PdfMcrNumber(new PdfNumber(0), p));
            p.addKid(new PdfMcrNumber(new PdfNumber(1), p));

            assertTrue(Tag.of(p).holdsOnlyMarkedContent());
        }
    }

    @Test
    void doesNotHoldOnlyMarkedContentWhenAnElementIsMixedIn() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            doc.getStructTreeRoot().addKid(p);
            p.addKid(new PdfMcrNumber(new PdfNumber(0), p));
            p.addKid(new PdfStructElem(doc, PdfName.Span));

            assertFalse(Tag.of(p).holdsOnlyMarkedContent());
        }
    }

    @Test
    void doesNotHoldOnlyMarkedContentWithAnObjRefAmongTheKids() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(0, 0, 10, 10));
            page.addAnnotation(annot);
            PdfStructElem link = new PdfStructElem(doc, PdfName.Link, page);
            doc.getStructTreeRoot().addKid(link);
            link.addKid(new PdfMcrNumber(new PdfNumber(0), link));
            link.addKid(new PdfObjRef(annot, link, doc.getNextStructParentIndex()));

            assertFalse(Tag.of(link).holdsOnlyMarkedContent());
        }
    }

    @Test
    void emptyElementDoesNotHoldOnlyMarkedContent() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(p);

            assertFalse(Tag.of(p).holdsOnlyMarkedContent());
        }
    }

    @Test
    void descendantMcrsReachThroughNestedElements() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page = doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div, page);
            doc.getStructTreeRoot().addKid(div);
            div.addKid(new PdfMcrNumber(new PdfNumber(0), div));
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            div.addKid(p);
            p.addKid(new PdfMcrNumber(new PdfNumber(1), p));

            List<Integer> mcids =
                    Tag.of(div).descendantMcrs().stream().map(mcr -> mcr.getMcid()).toList();
            assertEquals(List.of(0, 1), mcids);
        }
    }

    @Test
    void pageIsResolvedFromTheElementsKids() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            div.addKid(new PdfStructElem(doc, PdfName.P, page2));

            assertEquals(2, Tag.of(div).page());
        }
    }

    @Test
    void pageOfADirectElementComesFromItsPgEntry() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfDictionary dict = new PdfDictionary();
            dict.put(PdfName.Type, PdfName.StructElem);
            dict.put(PdfName.S, PdfName.P);
            dict.put(PdfName.Pg, page2.getPdfObject());

            assertEquals(2, Tag.of(new PdfStructElem(dict)).page());
        }
    }

    @Test
    void tagsOfFreshWrappersAroundOneElementAreEqual() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem div = new PdfStructElem(doc, PdfName.Div);
            doc.getStructTreeRoot().addKid(div);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            div.addKid(p);

            PdfStructElem rewrapped = (PdfStructElem) div.getKids().get(0);
            assertNotSame(p, rewrapped);
            assertEquals(Tag.of(p), Tag.of(rewrapped));
            assertEquals(Tag.of(p).hashCode(), Tag.of(rewrapped).hashCode());
        }
    }

    @Test
    void tagsOfDifferentElementsAreNotEqual() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();
            PdfStructElem first = new PdfStructElem(doc, PdfName.P);
            PdfStructElem second = new PdfStructElem(doc, PdfName.P);
            doc.getStructTreeRoot().addKid(first);
            doc.getStructTreeRoot().addKid(second);

            assertNotEquals(Tag.of(first), Tag.of(second));
        }
    }
}
