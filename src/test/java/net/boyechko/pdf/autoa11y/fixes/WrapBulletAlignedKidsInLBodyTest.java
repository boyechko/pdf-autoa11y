// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class WrapBulletAlignedKidsInLBodyTest extends PdfTestBase {

    @Test
    void wrapsCapturedKidsIntoSiblingList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            PdfStructElem span1 = new PdfStructElem(pdfDoc, PdfName.Span);
            PdfStructElem span2 = new PdfStructElem(pdfDoc, PdfName.Span);
            PdfStructElem span3 = new PdfStructElem(pdfDoc, PdfName.Span);
            sect.addKid(span1);
            sect.addKid(span2);
            sect.addKid(span3);

            DocContext ctx = new DocContext(pdfDoc);
            WrapBulletAlignedKidsInLBody fix =
                    new WrapBulletAlignedKidsInLBody(
                            sect, List.of(span2.getPdfObject(), span3.getPdfObject()), 100f, 1);
            fix.apply(ctx);

            assertEquals(
                    "Document[Sect[Span[]], L[LI[LBody[P[Span[], Span[]]]]]]",
                    StructTree.toRoleTree(document).toString());
            PdfStructElem list = (PdfStructElem) document.getKids().get(1);
            DocValue.Scribble scribble = StructTree.getScribble(list);
            assertTrue(scribble.toolAuthored());
            assertEquals("1 item", scribble.body());
        }
    }

    @Test
    void reregistersMovedNumericMcrsInParentTree() throws Exception {
        // Bare-int MCID kids (real marked content) must be re-registered under the new P so the
        // page ParentTree points at it; a raw K-array move leaves the reverse index pointing at
        // the old parent, producing an "inconsistent ParentTree mapping" that breaks extraction.
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            // Sect's /Pg lets its bare-int MCRs resolve their page for registration.
            sect.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            PdfMcr mcr0 = sect.addKid(new PdfMcrNumber(new PdfNumber(0), sect));
            PdfMcr mcr1 = sect.addKid(new PdfMcrNumber(new PdfNumber(1), sect));

            // Precondition: the ParentTree maps both MCIDs to sect.
            assertSame(sect.getPdfObject(), parentDictOf(root, page, 0));
            assertSame(sect.getPdfObject(), parentDictOf(root, page, 1));

            DocContext ctx = new DocContext(pdfDoc);
            new WrapBulletAlignedKidsInLBody(
                            sect, List.of(mcr0.getPdfObject(), mcr1.getPdfObject()), 100f, 1)
                    .apply(ctx);

            // After the move, the ParentTree must resolve both MCIDs to the new P element, not
            // to the now-pruned sect and not to nothing.
            PdfStructElem newP = descendantWithRole(document, "P");
            assertNotNull(newP, "Expected a new P element from the wrap");
            assertSame(
                    newP.getPdfObject(),
                    parentDictOf(root, page, 0),
                    "MCID 0 should be re-registered under the new P");
            assertSame(newP.getPdfObject(), parentDictOf(root, page, 1));
        }
    }

    @Test
    void locatesKidsByIdentityAfterIndicesShift() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            PdfStructElem span1 = new PdfStructElem(pdfDoc, PdfName.Span);
            PdfStructElem span2 = new PdfStructElem(pdfDoc, PdfName.Span);
            sect.addKid(span1);
            sect.addKid(span2);

            // Captured at detection time, when span1 was at index 0
            WrapBulletAlignedKidsInLBody fix =
                    new WrapBulletAlignedKidsInLBody(
                            sect, List.of(span1.getPdfObject(), span2.getPdfObject()), 100f, 1);

            // Another fix shifts the K array before this one applies
            PdfStructElem inserted = new PdfStructElem(pdfDoc, PdfName.Span);
            sect.addKid(0, inserted);

            DocContext ctx = new DocContext(pdfDoc);
            fix.apply(ctx);

            assertEquals(
                    "Document[Sect[Span[]], L[LI[LBody[P[Span[], Span[]]]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void doesNothingWhenCapturedKidsWereReparented() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            PdfStructElem span1 = new PdfStructElem(pdfDoc, PdfName.Span);
            PdfStructElem span2 = new PdfStructElem(pdfDoc, PdfName.Span);
            sect.addKid(span1);
            sect.addKid(span2);

            WrapBulletAlignedKidsInLBody fix =
                    new WrapBulletAlignedKidsInLBody(sect, List.of(span2.getPdfObject()), 100f, 1);

            // Another fix moves the captured kid out of the parent
            sect.removeKid(span2);
            document.addKid(span2);

            DocContext ctx = new DocContext(pdfDoc);
            fix.apply(ctx);

            assertEquals(
                    "Document[Sect[Span[]], Span[]]", StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void prunesParentEmptiedByTheMove() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            PdfStructElem span1 = new PdfStructElem(pdfDoc, PdfName.Span);
            PdfStructElem span2 = new PdfStructElem(pdfDoc, PdfName.Span);
            sect.addKid(span1);
            sect.addKid(span2);

            DocContext ctx = new DocContext(pdfDoc);
            WrapBulletAlignedKidsInLBody fix =
                    new WrapBulletAlignedKidsInLBody(
                            sect, List.of(span1.getPdfObject(), span2.getPdfObject()), 100f, 1);
            fix.apply(ctx);

            assertEquals(
                    "Document[L[LI[LBody[P[Span[], Span[]]]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void appendsToListImmediatelyBeforeParent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // A run-detected head of the same list already sits before the parent
            PdfStructElem existingList = new PdfStructElem(pdfDoc, PdfName.L);
            document.addKid(existingList);
            PdfStructElem existingLi = new PdfStructElem(pdfDoc, PdfName.LI);
            existingList.addKid(existingLi);

            PdfStructElem sect = new PdfStructElem(pdfDoc, PdfName.Sect);
            document.addKid(sect);
            PdfStructElem span1 = new PdfStructElem(pdfDoc, PdfName.Span);
            sect.addKid(span1);

            DocContext ctx = new DocContext(pdfDoc);
            WrapBulletAlignedKidsInLBody fix =
                    new WrapBulletAlignedKidsInLBody(sect, List.of(span1.getPdfObject()), 100f, 1);
            fix.apply(ctx);

            assertEquals(
                    "Document[L[LI[], LI[LBody[P[Span[]]]]]]",
                    StructTree.toRoleTree(document).toString());
            DocValue.Scribble scribble = StructTree.getScribble(existingList);
            assertTrue(scribble.toolAuthored());
            assertEquals("2 items", scribble.body());
        }
    }

    /** Returns the struct-elem dictionary the page ParentTree maps {@code mcid} to, or null. */
    private static com.itextpdf.kernel.pdf.PdfDictionary parentDictOf(
            PdfStructTreeRoot root, PdfPage page, int mcid) {
        PdfMcr mcr = root.findMcrByMcid(page.getPdfObject(), mcid);
        return mcr == null ? null : ((PdfStructElem) mcr.getParent()).getPdfObject();
    }

    /** Depth-first search for the first descendant with the given mapped role, or null. */
    private static PdfStructElem descendantWithRole(PdfStructElem node, String role) {
        if (role.equals(StructTree.mappedRole(node))) {
            return node;
        }
        for (var kid : node.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                PdfStructElem found = descendantWithRole(elem, role);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
