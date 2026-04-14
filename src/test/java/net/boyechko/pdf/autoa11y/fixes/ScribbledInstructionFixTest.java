/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class ScribbledInstructionFixTest extends PdfTestBase {

    @Test
    void addParentCreatesWrapper() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_PARENT Note[]").apply(ctx);

            PdfStructElem newParent = (PdfStructElem) p.getParent();
            assertEquals("Note", newParent.getRole().getValue());
            assertEquals("Document", ((PdfStructElem) newParent.getParent()).getRole().getValue());
        }
    }

    @Test
    void addParentPreservesPosition() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(h1);
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(target);
            PdfStructElem trailing = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(trailing);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(target, "!ADD_PARENT Note[]").apply(ctx);

            var kids = document.getKids();
            assertEquals(3, kids.size());
            assertEquals("H1", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("Note", ((PdfStructElem) kids.get(1)).getRole().getValue());
            assertEquals("P", ((PdfStructElem) kids.get(2)).getRole().getValue());
        }
    }

    @Test
    void addParentsBuildsNestedWrapperChain() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem span = new PdfStructElem(pdfDoc, new PdfName("Span"));
            document.addKid(span);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(span, "!ADD_PARENTS Reference[Link[P[]]]").apply(ctx);

            PdfStructElem p = (PdfStructElem) span.getParent();
            assertEquals("P", p.getRole().getValue());
            PdfStructElem link = (PdfStructElem) p.getParent();
            assertEquals("Link", link.getRole().getValue());
            PdfStructElem reference = (PdfStructElem) link.getParent();
            assertEquals("Reference", reference.getRole().getValue());
            assertEquals("Document", ((PdfStructElem) reference.getParent()).getRole().getValue());
        }
    }

    @Test
    void addParentRejectsBranchingChain() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem span = new PdfStructElem(pdfDoc, new PdfName("Span"));
            document.addKid(span);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ScribbledInstructionFix(span, "!ADD_PARENT Note[P[], Span[]]")
                                    .apply(ctx));
        }
    }

    @Test
    void addChildCreatesNestedStructure() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_CHILD Reference[Lbl[]]").apply(ctx);

            var kids = p.getKids();
            assertFalse(kids.isEmpty());
            PdfStructElem reference = (PdfStructElem) kids.get(0);
            assertEquals("Reference", reference.getRole().getValue());

            var refKids = reference.getKids();
            assertFalse(refKids.isEmpty());
            assertEquals("Lbl", ((PdfStructElem) refKids.get(0)).getRole().getValue());
        }
    }

    @Test
    void addChildCreatesEmptyElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_CHILD Span[]").apply(ctx);

            var kids = p.getKids();
            assertEquals(1, kids.size());
            assertEquals("Span", ((PdfStructElem) kids.get(0)).getRole().getValue());
        }
    }

    @Test
    void addChildCreatesSiblingElements() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            root.addKid(li);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILD Lbl[],LBody[]").apply(ctx);

            var kids = li.getKids();
            assertEquals(2, kids.size());
            assertEquals("Lbl", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("LBody", ((PdfStructElem) kids.get(1)).getRole().getValue());
        }
    }

    @Test
    void addChildrenWithRangeRedistributesMcrsIntoLblAndLBody() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            document.addKid(li);
            li.addKid(new PdfMcrNumber(page, li));
            li.addKid(new PdfMcrNumber(page, li));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], LBody[2..]").apply(ctx);

            var kids = li.getKids();
            assertEquals(2, kids.size());
            PdfStructElem lbl = (PdfStructElem) kids.get(0);
            PdfStructElem lbody = (PdfStructElem) kids.get(1);
            assertEquals("Lbl", lbl.getRole().getValue());
            assertEquals("LBody", lbody.getRole().getValue());
            assertEquals(1, lbl.getKids().size(), "Lbl should wrap 1 MCR");
            assertEquals(1, lbody.getKids().size(), "LBody should wrap 1 MCR");
        }
    }

    @Test
    void addChildrenWithOpenRangeWrapsAllTrailingKids() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            document.addKid(li);
            for (int i = 0; i < 4; i++) {
                li.addKid(new PdfMcrNumber(page, li));
            }

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], LBody[2..]").apply(ctx);

            var kids = li.getKids();
            assertEquals(2, kids.size());
            assertEquals(1, ((PdfStructElem) kids.get(0)).getKids().size());
            assertEquals(3, ((PdfStructElem) kids.get(1)).getKids().size());
        }
    }

    @Test
    void addChildrenWithRangeUpdatesStructElemParentPointer() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            document.addKid(li);
            PdfStructElem span1 = new PdfStructElem(pdfDoc, new PdfName("Span"));
            PdfStructElem span2 = new PdfStructElem(pdfDoc, new PdfName("Span"));
            li.addKid(span1);
            li.addKid(span2);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], LBody[2..]").apply(ctx);

            PdfStructElem lbl = (PdfStructElem) li.getKids().get(0);
            PdfStructElem lbody = (PdfStructElem) li.getKids().get(1);
            assertEquals(lbl.getPdfObject(), ((PdfStructElem) span1.getParent()).getPdfObject());
            assertEquals(lbody.getPdfObject(), ((PdfStructElem) span2.getParent()).getPdfObject());
        }
    }

    @Test
    void addChildrenSetsPageRefOnWrappersReceivingMcrs() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            document.addKid(li);
            li.addKid(new PdfMcrNumber(page, li));
            li.addKid(new PdfMcrNumber(page, li));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], LBody[2..]").apply(ctx);

            PdfStructElem lbl = (PdfStructElem) li.getKids().get(0);
            PdfStructElem lbody = (PdfStructElem) li.getKids().get(1);
            assertNotNull(lbl.getPdfObject().get(PdfName.Pg), "Lbl should have /Pg");
            assertNotNull(lbody.getPdfObject().get(PdfName.Pg), "LBody should have /Pg");
            assertEquals(page.getPdfObject(), lbl.getPdfObject().get(PdfName.Pg));
        }
    }

    @Test
    void addChildrenMixesEmptyWrapperBetweenRanges() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            document.addKid(li);
            li.addKid(new PdfMcrNumber(page, li));
            li.addKid(new PdfMcrNumber(page, li));

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], Note[], LBody[2..]").apply(ctx);

            var kids = li.getKids();
            assertEquals(3, kids.size());
            assertEquals("Lbl", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("Note", ((PdfStructElem) kids.get(1)).getRole().getValue());
            assertEquals("LBody", ((PdfStructElem) kids.get(2)).getRole().getValue());
        }
    }

    @Test
    void addChildrenRejectsGapInCoverage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            root.addKid(li);
            for (int i = 0; i < 3; i++) li.addKid(new PdfMcrNumber(page, li));

            DocContext ctx = new DocContext(pdfDoc);
            // Covers kid 1 and kid 3, but skips kid 2
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], LBody[3]")
                                    .apply(ctx));
        }
    }

    @Test
    void addChildrenRejectsOutOfOrderRanges() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            root.addKid(li);
            li.addKid(new PdfMcrNumber(page, li));
            li.addKid(new PdfMcrNumber(page, li));

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[2], LBody[1]")
                                    .apply(ctx));
        }
    }

    @Test
    void addChildrenRejectsPartialCoverage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            var page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI, page);
            root.addKid(li);
            for (int i = 0; i < 3; i++) li.addKid(new PdfMcrNumber(page, li));

            DocContext ctx = new DocContext(pdfDoc);
            // Only covers kids 1-2, leaving kid 3 uncovered
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ScribbledInstructionFix(li, "!ADD_CHILDREN Lbl[1], LBody[2]")
                                    .apply(ctx));
        }
    }

    @Test
    void addChildrenRejectsRangeRefInsideNestedWrapper() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new ScribbledInstructionFix(p, "!ADD_CHILDREN Reference[Lbl[1]]")
                                    .apply(ctx));
        }
    }

    @Test
    void artifactInstructionDelegatesToMistaggedArtifactFix() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertDoesNotThrow(() -> new ScribbledInstructionFix(p, "!ARTIFACT").apply(ctx));
        }
    }

    @Test
    void unsupportedInstructionThrows() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(p, "!UNKNOWN_OP Foo[]").apply(ctx));
        }
    }
}
