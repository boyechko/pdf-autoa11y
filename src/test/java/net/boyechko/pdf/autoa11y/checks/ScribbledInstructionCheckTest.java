// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import org.junit.jupiter.api.Test;

class ScribbledInstructionCheckTest extends PdfTestBase {

    @Test
    void detectsInstructionScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject()
                    .put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ADD_CHILD Reference[Lbl[]]"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix(), "Scribbled instruction issues should have a fix");
        }
    }

    @Test
    void ignoresNonInstructionScribbles() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "NeedsReview"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void ignoresElementsWithoutScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void detectsInstructionAmongOtherSegments() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // The instruction is not the whole scribble: a trailing note segment follows it.
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject()
                    .put(
                            PdfName.T,
                            new PdfString(
                                    SCRIBBLE_PREFIX
                                            + "!SET_ROLE H2"
                                            + StructTree.SCRIBBLE_SEPARATOR
                                            + "36.5pt"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix());
        }
    }

    @Test
    void detectsToolAuthoredInstruction() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // Tool-authored instruction: ':' authorship mark before the '!' directive.
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            StructTree.setToolScribble(p, "!SET_ROLE H2");
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix());
        }
    }

    @Test
    void ignoresToolAuthoredFinding() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            // A '?' finding is informational, not an instruction; it must never execute.
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            StructTree.setToolScribble(
                    p, "?H3 (expected H2)" + StructTree.SCRIBBLE_SEPARATOR + "23.75pt");
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void detectsMultipleInstructionScribbles() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p1.getPdfObject()
                    .put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ADD_CHILD Reference[Lbl[]]"));
            document.addKid(p1);

            PdfStructElem p2 = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p2.getPdfObject()
                    .put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ADD_CHILD Reference[Lbl[]]"));
            document.addKid(p2);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(2, issues.size());
        }
    }

    @Test
    void detectsAddParentScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ADD_PARENT Note[]"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix());
        }
    }

    @Test
    void detectsArtifactScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ARTIFACT"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix());
        }
    }

    @Test
    void skipsChildrenOfArtifactInstruction() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div, firstPage);
            div.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ARTIFACT"));
            document.addKid(div);

            PdfStructElem child = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            child.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ARTIFACT"));
            div.addKid(child);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size(), "Should only flag the parent, not the child");
        }
    }

    @Test
    void reorderPositionMarkersFeedTheParentInstructionOnly() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            StructTree.setScribble(document, "!REORDER_KIDS");

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            document.addKid(h1);
            StructTree.setScribble(h1, "REORDER 2");

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            document.addKid(p);
            StructTree.setScribble(p, "REORDER 1");

            DocContext ctx = new DocContext(pdfDoc);
            IssueList issues = runCheck(pdfDoc);
            assertEquals(1, issues.size(), "Only the parent's !REORDER_KIDS is an instruction");
            issues.get(0).fix().apply(ctx);

            var kids = document.getKids();
            assertEquals("P", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("H1", ((PdfStructElem) kids.get(1)).getRole().getValue());
        }
    }

    @Test
    void ancestorInstructionsApplyBeforeDescendantOnes() throws Exception {
        // A paragraph tagged one line per item: each LI breaks out before the !MERGE on its P
        // runs, so every P finds the previously hoisted one as its preceding sibling.
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            root.addKid(div);
            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            div.addKid(list);

            PdfStructElem[] paragraphs = new PdfStructElem[3];
            for (int i = 0; i < paragraphs.length; i++) {
                PdfStructElem item = new PdfStructElem(pdfDoc, PdfName.LI);
                list.addKid(item);
                StructTree.setScribble(item, "!BREAK_OUT");
                PdfStructElem body = new PdfStructElem(pdfDoc, PdfName.LBody);
                item.addKid(body);
                paragraphs[i] = new PdfStructElem(pdfDoc, PdfName.P);
                body.addKid(paragraphs[i]);
                paragraphs[i].addKid(new PdfStructElem(pdfDoc, PdfName.Span));
                if (i > 0) {
                    StructTree.setScribble(paragraphs[i], "!MERGE");
                }
            }

            IssueList issues = runCheck(pdfDoc);
            assertEquals(5, issues.size());
            IssueList resolved = issues.applyFixes(new DocContext(pdfDoc));

            assertEquals(5, resolved.size(), "Every instruction should succeed");
            assertEquals(1, div.getKids().size(), "Div should hold only the merged paragraph");
            PdfStructElem survivor = (PdfStructElem) div.getKids().get(0);
            assertEquals(paragraphs[0].getPdfObject(), survivor.getPdfObject());
            assertEquals(3, survivor.getKids().size(), "Survivor should hold every line's kid");
        }
    }

    @Test
    void ignoresRegularTitle() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject().put(PdfName.T, new PdfString("Chapter 1"));
            document.addKid(h1);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    private IssueList runCheck(PdfDocument pdfDoc) {
        StructTreeWalker walker = new StructTreeWalker();
        walker.addVisitor(new ScribbledInstructionCheck());
        return walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }
}
