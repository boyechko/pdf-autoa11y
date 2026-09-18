// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.StructTreeOrderFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class StructTreeOrderCheckTest extends PdfTestBase {

    @Test
    void elementsInPageOrderProducesNoIssues() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            document.addKid(p1);
            document.addKid(p2);

            addMcr(page1, p1);
            addMcr(page2, p2);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "Elements in page order should produce no issues");
        }
    }

    @Test
    void elementsOutOfPageOrderDetected() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Add in reverse order: page2 element first, then page1
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            document.addKid(p2);
            document.addKid(p1);

            addMcr(page2, p2);
            addMcr(page1, p1);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertFalse(issues.isEmpty(), "Out-of-order elements should be detected");
            assertEquals(
                    IssueType.STRUCT_TREE_OUT_OF_ORDER,
                    issues.stream().findFirst().orElseThrow().type());
        }
    }

    @Test
    void nestedOutOfOrderDetected() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfPage page3 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Two Sect elements in correct order
            PdfStructElem sect1 = new PdfStructElem(doc, PdfName.Sect, page1);
            PdfStructElem sect2 = new PdfStructElem(doc, PdfName.Sect, page2);
            document.addKid(sect1);
            document.addKid(sect2);

            addMcr(page1, sect1);

            // Within sect2, children are reversed: page3 before page2
            PdfStructElem p3 = new PdfStructElem(doc, PdfName.P, page3);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            sect2.addKid(p3);
            sect2.addKid(p2);

            addMcr(page3, p3);
            addMcr(page2, p2);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertEquals(
                    1,
                    issues.stream()
                            .filter(i -> i.type() == IssueType.STRUCT_TREE_OUT_OF_ORDER)
                            .count(),
                    "Only sect2 has out-of-order children; Document level is correctly ordered");
        }
    }

    @Test
    void singleChildProducesNoIssues() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            document.addKid(p1);

            addMcr(page1, p1);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "Single child should not trigger order check");
        }
    }

    @Test
    void samePageDifferentMcidsOutOfOrder() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // pEarlier should appear first (lower mcid), pLater second
            PdfStructElem pEarlier = new PdfStructElem(doc, PdfName.P, page1);
            PdfStructElem pLater = new PdfStructElem(doc, PdfName.P, page1);

            // Add to tree in wrong order: pLater first, pEarlier second
            document.addKid(pLater);
            document.addKid(pEarlier);

            // pEarlier gets mcid 0 (first on page), pLater gets mcid 1
            addMcr(page1, pEarlier);
            addMcr(page1, pLater);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertFalse(
                    issues.isEmpty(),
                    "Same-page elements with reversed MCIDs should be out of order");
        }
    }

    @Test
    void fixReordersChildrenToMatchReadingOrder() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfPage page3 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Add in reverse page order: page3, page2, page1
            PdfStructElem p3 = new PdfStructElem(doc, PdfName.P, page3);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            document.addKid(p3);
            document.addKid(p2);
            document.addKid(p1);

            addMcr(page3, p3);
            addMcr(page2, p2);
            addMcr(page1, p1);

            DocContext ctx = new DocContext(doc);

            // Detect and apply per-element fixes
            IssueList issues = new StructTreeOrderCheck().findIssues(ctx);
            assertFalse(issues.isEmpty(), "Precondition: should detect out-of-order");
            applyAllFixes(issues, ctx);

            // Verify children are now in page order
            List<PdfStructElem> kids = StructTree.childrenOf(document, PdfStructElem.class);
            assertEquals(3, kids.size());
            assertTrue(
                    StructTree.isSameElement(kids.get(0), p1),
                    "First child should be page-1 element");
            assertTrue(
                    StructTree.isSameElement(kids.get(1), p2),
                    "Second child should be page-2 element");
            assertTrue(
                    StructTree.isSameElement(kids.get(2), p3),
                    "Third child should be page-3 element");

            // Re-check: should now be clean
            IssueList afterIssues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(afterIssues.isEmpty(), "After fix, no order issues should remain");
        }
    }

    @Test
    void fixIsIdempotentOnAlreadyOrderedElement() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            document.addKid(p1);
            document.addKid(p2);

            addMcr(page1, p1);
            addMcr(page2, p2);

            // Directly apply a fix to an already-ordered element
            DocContext ctx = new DocContext(doc);
            new StructTreeOrderFix(document, new HashMap<>()).apply(ctx);

            // Order should be unchanged
            List<PdfStructElem> kids = StructTree.childrenOf(document, PdfStructElem.class);
            assertTrue(StructTree.isSameElement(kids.get(0), p1));
            assertTrue(StructTree.isSameElement(kids.get(1), p2));
        }
    }

    @Test
    void multipleOutOfOrderParentsProduceMultipleIssues() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfPage page3 = doc.addNewPage();
            PdfPage page4 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Sect1: children out of order (page2 before page1)
            PdfStructElem sect1 = new PdfStructElem(doc, PdfName.Sect, page1);
            document.addKid(sect1);
            PdfStructElem s1p2 = new PdfStructElem(doc, PdfName.P, page2);
            PdfStructElem s1p1 = new PdfStructElem(doc, PdfName.P, page1);
            sect1.addKid(s1p2);
            sect1.addKid(s1p1);
            addMcr(page2, s1p2);
            addMcr(page1, s1p1);

            // Sect2: children out of order (page4 before page3)
            PdfStructElem sect2 = new PdfStructElem(doc, PdfName.Sect, page3);
            document.addKid(sect2);
            PdfStructElem s2p4 = new PdfStructElem(doc, PdfName.P, page4);
            PdfStructElem s2p3 = new PdfStructElem(doc, PdfName.P, page3);
            sect2.addKid(s2p4);
            sect2.addKid(s2p3);
            addMcr(page4, s2p4);
            addMcr(page3, s2p3);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            long orderIssues =
                    issues.stream()
                            .filter(i -> i.type() == IssueType.STRUCT_TREE_OUT_OF_ORDER)
                            .count();
            assertEquals(2, orderIssues, "Each out-of-order parent should produce its own issue");

            // Apply all fixes and verify
            DocContext ctx = new DocContext(doc);
            applyAllFixes(issues, ctx);

            IssueList afterIssues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(afterIssues.isEmpty(), "All order issues should be resolved");
        }
    }

    // ── Reading order taken from the page ───────────────────────────

    @Test
    void contentPaintedLateIsPlacedByGeometryNotByItsMcid() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem document = newDocument(doc);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            // Tree order follows the page: heading above, body below.
            PdfStructElem heading = addChild(doc, page, document, PdfName.H1);
            PdfStructElem body = addChild(doc, page, document, PdfName.P);

            // The body is painted first, so the heading above it gets the higher MCID, the way a
            // splitting fix leaves a page it has re-marked.
            paint(page, canvas, font, body, 72, 600, "Body text below the heading");
            paint(page, canvas, font, heading, 72, 700, "Heading at the top");
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "A late MCID on higher content is not a disorder");
        }
    }

    @Test
    void detectsDisorderThatAscendingMcidsConceal() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem document = newDocument(doc);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            // Tree order puts the lower content first, and the MCIDs ascend in that same wrong
            // order, so only the geometry gives the disorder away.
            PdfStructElem lower = addChild(doc, page, document, PdfName.P);
            PdfStructElem upper = addChild(doc, page, document, PdfName.P);
            paint(page, canvas, font, lower, 72, 600, "Lower on the page");
            paint(page, canvas, font, upper, 72, 700, "Higher on the page");
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertEquals(
                    1,
                    issues.stream()
                            .filter(i -> i.type() == IssueType.STRUCT_TREE_OUT_OF_ORDER)
                            .count());
        }
    }

    @Test
    void fixPutsSiblingsOnOnePageIntoReadingOrder() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem document = newDocument(doc);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            PdfStructElem lower = addChild(doc, page, document, PdfName.P);
            PdfStructElem upper = addChild(doc, page, document, PdfName.P);
            paint(page, canvas, font, lower, 72, 600, "Lower on the page");
            paint(page, canvas, font, upper, 72, 700, "Higher on the page");
        }

        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(pdfFile.toString()),
                        new PdfWriter(testOutputStream("fixPutsSiblingsIntoReadingOrder.pdf")))) {
            DocContext ctx = new DocContext(doc);
            applyAllFixes(new StructTreeOrderCheck().findIssues(ctx), ctx);

            assertEquals(
                    List.of("Higher on the page", "Lower on the page"),
                    childTexts(documentOf(doc), ctx));
        }
    }

    @Test
    void siblingsOnOneLineAreOrderedLeftToRight() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem document = newDocument(doc);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            // Both sit on one baseline, so the band ties and only the left edge separates them.
            PdfStructElem right = addChild(doc, page, document, PdfName.P);
            PdfStructElem left = addChild(doc, page, document, PdfName.P);
            paint(page, canvas, font, right, 300, 700, "second");
            paint(page, canvas, font, left, 72, 700, "first");
        }

        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(pdfFile.toString()),
                        new PdfWriter(testOutputStream("siblingsOnOneLine.pdf")))) {
            DocContext ctx = new DocContext(doc);
            IssueList issues = new StructTreeOrderCheck().findIssues(ctx);
            assertFalse(issues.isEmpty(), "Content further left on one line is read first");

            applyAllFixes(issues, ctx);
            assertEquals(List.of("first", "second"), childTexts(documentOf(doc), ctx));
        }
    }

    // ── Table rows ──────────────────────────────────────────────────

    @Test
    void staggeredTableRowCellsAreNotOutOfOrder() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem row = newTableRow(doc, page, newDocument(doc));
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            paintStaggeredRow(page, canvas, font, row, PdfName.TH);
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "A row's cells are read left to right, not down the page");
        }
    }

    @Test
    void staggeredCellsOutsideARowAreStillOrderedDownThePage() throws Exception {
        // The left-to-right rule is chosen by the parent's role, so the same geometry under a
        // parent that is not a TR is still read down the page. This is the documented limit of
        // the exemption, not a property worth preserving.
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem document = newDocument(doc);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            paintStaggeredRow(page, canvas, font, document, PdfName.P);
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertFalse(issues.isEmpty(), "Outside a row, the taller cell's higher top wins");
        }
    }

    @Test
    void fixOrdersTableRowCellsLeftToRight() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem row = newTableRow(doc, page, newDocument(doc));
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            // Cells stand in the tree right to left.
            PdfStructElem right = addChild(doc, page, row, PdfName.TH);
            PdfStructElem middle = addChild(doc, page, row, PdfName.TH);
            PdfStructElem left = addChild(doc, page, row, PdfName.TH);
            paint(page, canvas, font, right, 400, 700, "TOEFL");
            paint(page, canvas, font, middle, 200, 712, "IELTS only,", "before", "June 2017");
            paint(page, canvas, font, left, 50, 700, "ELP");
        }

        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(pdfFile.toString()),
                        new PdfWriter(testOutputStream("fixOrdersRowCells.pdf")))) {
            DocContext ctx = new DocContext(doc);
            IssueList issues = new StructTreeOrderCheck().findIssues(ctx);
            assertFalse(issues.isEmpty(), "Precondition: cells stand right to left");

            applyAllFixes(issues, ctx);
            PdfStructElem table =
                    StructTree.childrenOf(documentOf(doc), PdfStructElem.class).get(0);
            PdfStructElem row = StructTree.childrenOf(table, PdfStructElem.class).get(0);
            assertEquals(
                    List.of("ELP", "IELTS only, before June 2017", "TOEFL"), childTexts(row, ctx));
        }
    }

    // ── Elements the check declines to judge ────────────────────────

    @Test
    void childPaintingNothingSuppressesTheCheck() throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            PdfPage page = newTaggedPage(doc);
            PdfStructElem document = newDocument(doc);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = helvetica();

            PdfStructElem lower = addChild(doc, page, document, PdfName.P);
            PdfStructElem upper = addChild(doc, page, document, PdfName.P);
            addChild(doc, page, document, PdfName.P); // never painted, so never locatable
            paint(page, canvas, font, lower, 72, 600, "Lower on the page");
            paint(page, canvas, font, upper, 72, 700, "Higher on the page");
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "An unlocatable child should withhold the whole verdict");
        }
    }

    // ── Fixture helpers ─────────────────────────────────────────────

    /** Height of one painted line, which is also the font size the fixtures use. */
    private static final float LINE_HEIGHT = 12f;

    private static PdfFont helvetica() throws Exception {
        return PdfFontFactory.createFont(StandardFonts.HELVETICA);
    }

    private static PdfPage newTaggedPage(PdfDocument doc) {
        doc.setTagged();
        return doc.addNewPage();
    }

    /** Adds the Document element that every fixture hangs its content from. */
    private static PdfStructElem newDocument(PdfDocument doc) {
        PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
        doc.getStructTreeRoot().addKid(document);
        return document;
    }

    /** Adds a Table holding one TR and returns the row. */
    private static PdfStructElem newTableRow(PdfDocument doc, PdfPage page, PdfStructElem parent) {
        return addChild(doc, page, addChild(doc, page, parent, PdfName.Table), PdfName.TR);
    }

    /** Appends a child of the given role, fixing its place in tree order. */
    private static PdfStructElem addChild(
            PdfDocument doc, PdfPage page, PdfStructElem parent, PdfName role) {
        PdfStructElem child = new PdfStructElem(doc, role, page);
        parent.addKid(child);
        return child;
    }

    /**
     * Paints three cells left to right, the middle one wrapping onto three lines so that it starts
     * higher up the page than its single-line neighbours.
     */
    private static void paintStaggeredRow(
            PdfPage page, PdfCanvas canvas, PdfFont font, PdfStructElem parent, PdfName role)
            throws Exception {
        PdfDocument doc = page.getDocument();
        PdfStructElem left = addChild(doc, page, parent, role);
        PdfStructElem middle = addChild(doc, page, parent, role);
        PdfStructElem right = addChild(doc, page, parent, role);
        paint(page, canvas, font, left, 50, 700, "ELP");
        paint(page, canvas, font, middle, 200, 712, "IELTS only,", "before", "June 2017");
        paint(page, canvas, font, right, 400, 700, "TOEFL");
    }

    /**
     * Paints an element's lines with the first baseline at {@code (x, y)}. MCIDs are minted in call
     * order, so painting late gives an element a high MCID wherever it sits on the page.
     */
    private static void paint(
            PdfPage page,
            PdfCanvas canvas,
            PdfFont font,
            PdfStructElem elem,
            float x,
            float y,
            String... lines) {
        PdfMcrNumber mcr = new PdfMcrNumber(page, elem);
        elem.addKid(mcr);

        PdfDictionary props = new PdfDictionary();
        props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));
        canvas.beginMarkedContent(elem.getRole(), props);
        canvas.beginText();
        canvas.setFontAndSize(font, LINE_HEIGHT);
        canvas.moveText(x, y);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                canvas.moveText(0, -LINE_HEIGHT);
            }
            canvas.showText(lines[i]);
        }
        canvas.endText();
        canvas.endMarkedContent();
    }

    /** Returns the Document element of a reopened fixture. */
    private static PdfStructElem documentOf(PdfDocument doc) {
        return (PdfStructElem) doc.getStructTreeRoot().getKids().get(0);
    }

    /** Returns the text painted for each child of an element, in tree order. */
    private static List<String> childTexts(PdfStructElem parent, DocContext ctx) {
        List<String> texts = new ArrayList<>();
        for (PdfStructElem child : StructTree.childrenOf(parent, PdfStructElem.class)) {
            StringBuilder text = new StringBuilder();
            for (PdfMcr mcr : StructTree.descendantsOf(child, PdfMcr.class)) {
                text.append(ctx.getMcidText(StructTree.pageOf(mcr), mcr.getMcid()));
            }
            // Normalized, since how the extractor separates an element's lines is beside the point
            texts.add(text.toString().replaceAll("\\s+", " ").trim());
        }
        return texts;
    }

    /** Adds a simple MCR to a structure element on the given page. */
    private void addMcr(PdfPage page, PdfStructElem elem) {
        PdfMcrNumber mcr = new PdfMcrNumber(page, elem);
        elem.addKid(mcr);
    }

    /** Applies all fixes from the issue list. */
    private void applyAllFixes(IssueList issues, DocContext ctx) throws Exception {
        for (Issue issue : issues) {
            IssueFix fix = issue.fix();
            if (fix != null) {
                fix.apply(ctx);
            }
        }
    }
}
