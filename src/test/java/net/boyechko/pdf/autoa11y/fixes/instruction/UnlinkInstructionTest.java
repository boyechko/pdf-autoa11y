// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class UnlinkInstructionTest extends PdfTestBase {

    @Test
    void unlinkPromotesStructElemKidAndRemovesLink() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(p);

            PdfStructElem link = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(link);
            PdfStructElem span = new PdfStructElem(pdfDoc, PdfName.Span, page);
            link.addKid(span);

            PdfLinkAnnotation annot =
                    new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14))
                            .setAction(PdfAction.createURI("https://bogus.10"));
            page.addAnnotation(-1, annot, false);
            int spi = pdfDoc.getNextStructParentIndex();
            link.addKid(new PdfObjRef(annot, link, spi));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(link, "!UNLINK").apply(ctx);

            var kids = p.getKids();
            assertEquals(1, kids.size(), "P should now hold the promoted Span only");
            assertEquals("Span", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals(
                    p.getPdfObject(),
                    ((PdfStructElem) span.getParent()).getPdfObject(),
                    "Span's parent pointer should be rebound to P");
        }
    }

    @Test
    void unlinkPreservesPositionBetweenSiblings() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(p);

            PdfStructElem before = new PdfStructElem(pdfDoc, PdfName.Span, page);
            p.addKid(before);
            PdfStructElem link = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(link);
            PdfStructElem after = new PdfStructElem(pdfDoc, PdfName.Span, page);
            p.addKid(after);

            PdfStructElem linkInner = new PdfStructElem(pdfDoc, PdfName.Span, page);
            link.addKid(linkInner);

            PdfLinkAnnotation annot =
                    new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14))
                            .setAction(PdfAction.createURI("https://bogus.10"));
            page.addAnnotation(-1, annot, false);
            int spi = pdfDoc.getNextStructParentIndex();
            link.addKid(new PdfObjRef(annot, link, spi));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(link, "!UNLINK").apply(ctx);

            var kids = p.getKids();
            assertEquals(3, kids.size(), "P should contain before + promoted inner + after");
            assertEquals(before.getPdfObject(), ((PdfStructElem) kids.get(0)).getPdfObject());
            assertEquals(linkInner.getPdfObject(), ((PdfStructElem) kids.get(1)).getPdfObject());
            assertEquals(after.getPdfObject(), ((PdfStructElem) kids.get(2)).getPdfObject());
        }
    }

    @Test
    void unlinkPromotesMcrKidAndInheritsPageRef() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            // P has no /Pg — only Link carries it.
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            PdfStructElem link = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(link);
            link.addKid(new PdfMcrNumber(page, link));

            PdfLinkAnnotation annot =
                    new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14))
                            .setAction(PdfAction.createURI("https://bogus.10"));
            page.addAnnotation(-1, annot, false);
            int spi = pdfDoc.getNextStructParentIndex();
            link.addKid(new PdfObjRef(annot, link, spi));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(link, "!UNLINK").apply(ctx);

            var kids = p.getKids();
            assertEquals(1, kids.size(), "P should contain the promoted MCR only");
            IStructureNode promoted = kids.get(0);
            assertEquals(
                    page.getPdfObject().getIndirectReference(),
                    ((com.itextpdf.kernel.pdf.tagging.PdfMcr) promoted).getPageIndirectReference(),
                    "Promoted MCR must still resolve its page via ancestor /Pg");
            assertNotNull(
                    p.getPdfObject().get(PdfName.Pg),
                    "P should have gained /Pg when a bare-int MCR was promoted into it");
        }
    }

    @Test
    void unlinkRemovesAnnotationFromPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(p);

            PdfStructElem link = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(link);

            PdfLinkAnnotation annot =
                    new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14))
                            .setAction(PdfAction.createURI("https://bogus.10"));
            page.addAnnotation(-1, annot, false);
            int spi = pdfDoc.getNextStructParentIndex();
            link.addKid(new PdfObjRef(annot, link, spi));

            assertEquals(1, page.getAnnotations().size(), "Precondition: page has the annotation");

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(link, "!UNLINK").apply(ctx);

            PdfArray annots = page.getPdfObject().getAsArray(PdfName.Annots);
            assertTrue(
                    annots == null || annots.isEmpty(),
                    "Page /Annots should no longer contain the Link annotation");
        }
    }

    @Test
    void unlinkRejectsNonLinkElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(p, "!UNLINK").apply(ctx));
        }
    }
}
