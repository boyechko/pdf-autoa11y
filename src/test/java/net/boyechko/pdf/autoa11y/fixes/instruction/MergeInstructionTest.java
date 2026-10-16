// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Map;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MergeInstructionTest extends PdfTestBase {

    @Test
    void mergeFoldsKidsIntoPrecedingSibling() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(first);
            first.addKid(new PdfMcrNumber(page, first));
            PdfStructElem second = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(second);
            second.addKid(new PdfMcrNumber(page, second));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(second, "!MERGE").apply(ctx);

            assertEquals(1, document.getKids().size(), "Absorbed element should be gone");
            assertEquals(2, first.getKids().size(), "Both MCRs should live under the survivor");
            assertTrue(first.getKids().stream().allMatch(kid -> kid instanceof PdfMcr));
        }
    }

    @Test
    void mergeChainsAcrossConsecutiveSiblings() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem[] paragraphs = new PdfStructElem[4];
            for (int i = 0; i < paragraphs.length; i++) {
                paragraphs[i] = new PdfStructElem(pdfDoc, PdfName.P, page);
                document.addKid(paragraphs[i]);
                paragraphs[i].addKid(new PdfMcrNumber(page, paragraphs[i]));
            }

            // Fixes run in traversal order, so each resolves its preceding sibling afresh.
            DocContext ctx = new DocContext(pdfDoc);
            for (int i = 1; i < paragraphs.length; i++) {
                new ScribbledInstructionFix(paragraphs[i], "!MERGE").apply(ctx);
            }

            assertEquals(1, document.getKids().size());
            assertEquals(4, paragraphs[0].getKids().size(), "All four MCRs should have collapsed");
        }
    }

    @Test
    void mergeRefusesRoleMismatchLeavingTreeUntouched() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem heading = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(heading);
            PdfStructElem paragraph = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(paragraph);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(paragraph, "!MERGE").apply(ctx));
            assertEquals(2, document.getKids().size(), "Refusal should be side-effect free");
        }
    }

    @Test
    void mergeAcceptsDifferentlyNamedSiblingsWithSameMappedRole() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            RoleMap.replace(pdfDoc, Map.of("FirstParagraph", "P", "SecondParagraph", "P"));
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem first = new PdfStructElem(pdfDoc, RoleMap.toPdfName("FirstParagraph"));
            document.addKid(first);
            PdfStructElem second = new PdfStructElem(pdfDoc, RoleMap.toPdfName("SecondParagraph"));
            document.addKid(second);

            new ScribbledInstructionFix(second, "!MERGE").apply(new DocContext(pdfDoc));

            assertEquals(1, document.getKids().size());
            assertSame(
                    first.getPdfObject(),
                    ((PdfStructElem) document.getKids().get(0)).getPdfObject());
        }
    }

    @Test
    void mergeRefusesElementWithoutPrecedingSibling() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem onlyKid = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(onlyKid);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(onlyKid, "!MERGE").apply(ctx));
        }
    }

    @Test
    void mergeRefusesMarkedContentPrecedingSibling() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document, page);
            root.addKid(document);
            document.addKid(new PdfMcrNumber(page, document));
            PdfStructElem paragraph = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(paragraph);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(paragraph, "!MERGE").apply(ctx));
            assertEquals(2, document.getKids().size(), "Refusal should be side-effect free");
        }
    }

    @Test
    void mergeRefusesElementDetachedFromItsParent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(first);
            PdfStructElem detached = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(detached);

            // removeKid drops the element from /K but leaves its /P pointing at the parent,
            // so getParent() still succeeds while the parent no longer lists it as a kid.
            document.removeKid(detached);
            assertNotNull(detached.getParent(), "Precondition: /P is left dangling");

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(detached, "!MERGE").apply(ctx));
            assertEquals(1, document.getKids().size(), "Refusal should be side-effect free");
        }
    }

    @Test
    void mergePinsPageOnTargetLackingPg() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // Neither paragraph carries /Pg itself; the absorbed one resolves through its kid.
            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(first);
            PdfStructElem second = new PdfStructElem(pdfDoc, PdfName.P, page);
            document.addKid(second);
            second.addKid(new PdfMcrNumber(page, second));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(second, "!MERGE").apply(ctx);

            assertNotNull(
                    first.getPdfObject().get(PdfName.Pg),
                    "Survivor should have gained /Pg so the moved bare-int MCR resolves");
            assertEquals(page.getPdfObject(), first.getPdfObject().get(PdfName.Pg));
        }
    }

    @Test
    void mergeKeepsCrossPageMcrOnItsOwnPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfPage secondPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            document.addKid(first);
            first.addKid(new PdfMcrNumber(firstPage, first));
            PdfStructElem second = new PdfStructElem(pdfDoc, PdfName.P, secondPage);
            document.addKid(second);
            second.addKid(new PdfMcrNumber(secondPage, second));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(second, "!MERGE").apply(ctx);

            var kids = first.getKids();
            assertEquals(2, kids.size());
            assertEquals(1, StructTree.pageOf((PdfMcr) kids.get(0)));
            assertEquals(
                    2,
                    StructTree.pageOf((PdfMcr) kids.get(1)),
                    "Moved MCR must still resolve to its original page");
        }
    }

    @Test
    void mergeBreadcrumbCountsAbsorbedSiblings() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(first);
            StructTree.setScribble(first, "keep me");
            PdfStructElem second = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(second);
            PdfStructElem third = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(third);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(second, "!MERGE").apply(ctx);
            new ScribbledInstructionFix(third, "!MERGE").apply(ctx);

            var scribble = StructTree.getScribble(first);
            assertNotNull(scribble);
            assertTrue(
                    scribble.segments().contains("keep me"),
                    "Existing scribble segments should survive");
            assertTrue(
                    scribble.segments()
                            .contains(
                                    ScribbledInstructionFix.INSTRUCTION_TAG + " MERGE absorbed 2"),
                    "Receipt should accumulate both absorbed siblings: " + scribble.value());
        }
    }
}
