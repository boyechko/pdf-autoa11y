// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later

package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.StructTree.Node;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class MistaggedListCheckTest extends PdfTestBase {

    private static final String SHARED_URI = "https://uw.edu/catalog/Education-875.html";

    /**
     * Catalog pages whose paragraphs lump many bulleted items into one tag: P #43 covers seven
     * bullets over sixteen lines, P #69 and P #72 lump nine items across two LIs of one list, and P
     * #76 opens mid-item ahead of ten hollow sub-bullets.
     */
    private static final Path CATALOG_PDF = Path.of("src/test/resources/catalog_098-102.pdf");

    private static void walkWith(PdfDocument pdfDoc, MistaggedListCheck check) throws Exception {
        StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
        walker.addVisitor(check);
        walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }

    // == Bullet census evidence ==========================================

    /** Maps object number to the census issue's message for every lumped element found. */
    private static Map<Integer, String> lumpedElementsIn(MistaggedListCheck check) {
        return check.getIssues().stream()
                .filter(issue -> issue.type() == IssueType.LIST_ITEMS_LUMPED)
                .collect(
                        Collectors.toMap(
                                issue -> issue.where().objNum(),
                                Issue::message,
                                (first, second) -> first,
                                LinkedHashMap::new));
    }

    private static Map<Integer, String> censusOfCatalog() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(CATALOG_PDF.toString()))) {
            walkWith(pdfDoc, check);
        }
        return lumpedElementsIn(check);
    }

    @Test
    void derivesItemLineCountsFromBulletSpacing() throws Exception {
        // P #43's sixteen lines carry seven bullets, so its items wrap 2, 3, 1, 3, 3, 2 and 2
        // lines — a spec no whole-element comparison could recover.
        Map<Integer, String> lumped = censusOfCatalog();

        assertTrue(lumped.containsKey(43), "P #43 should be reported: " + lumped);
        assertTrue(lumped.get(43).contains("2,3,1,3,3,2,2"), lumped.get(43));
        assertTrue(lumped.get(43).contains("7 bullet glyphs"), lumped.get(43));
    }

    @Test
    void censusesElementsAlreadyTaggedAsListItems() throws Exception {
        // P #69 and P #72 are the sole paragraphs of two LIs in one list, yet between them
        // they lump nine items; the list looks well-formed from the structure alone.
        Map<Integer, String> lumped = censusOfCatalog();

        assertTrue(lumped.containsKey(69), "P #69 should be reported: " + lumped);
        assertTrue(lumped.get(69).contains("1,1,1,1,1,1"), lumped.get(69));
        assertTrue(lumped.containsKey(72), "P #72 should be reported: " + lumped);
        assertTrue(lumped.get(72).contains("1,2,1"), lumped.get(72));
    }

    @Test
    void reportsWithoutFixWhenLeadingLinesContinuePreviousItem() throws Exception {
        // P #76 opens with a line finishing the item P #75 began, then runs ten hollow
        // sub-bullets. Splitting on the bullets would hand that line to the wrong item.
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(CATALOG_PDF.toString()))) {
            walkWith(pdfDoc, check);
        }

        Issue issue =
                check.getIssues().stream()
                        .filter(i -> i.type() == IssueType.LIST_ITEMS_LUMPED)
                        .filter(i -> Integer.valueOf(76).equals(i.where().objNum()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("P #76 not reported"));

        assertTrue(issue.message().contains("10 bullet glyphs"), issue.message());
        assertNull(issue.fix(), "a mid-item start is reported for review, not split");
    }

    @Test
    void ignoresElementCoveringASingleBullet() throws Exception {
        // P #52, P #75 and P #89 are each one bulleted line: correctly one item apiece.
        Map<Integer, String> lumped = censusOfCatalog();

        assertFalse(lumped.containsKey(52), "P #52 covers one bullet: " + lumped);
        assertFalse(lumped.containsKey(75), "P #75 covers one bullet: " + lumped);
        assertFalse(lumped.containsKey(89), "P #89 covers one bullet: " + lumped);
    }

    @Test
    void ignoresMultiLineElementWithNoBullets() throws Exception {
        // P #93's twelve lines and P #114's nine carry no bullets at all: plain prose.
        Map<Integer, String> lumped = censusOfCatalog();

        assertFalse(lumped.containsKey(93), "P #93 has no bullets: " + lumped);
        assertFalse(lumped.containsKey(114), "P #114 has no bullets: " + lumped);
    }

    // == Indent evidence =================================================

    private Path createTestPdfWithParagraphRun(
            int numParagraphs,
            String paragraphText,
            float headingMargin,
            float paragraphMargin,
            int paragraphIndent)
            throws Exception {
        Path pdfPath = testOutputPath();
        try (PdfWriter writer = new PdfWriter(pdfPath.toString());
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            Style headingStyle =
                    new Style().setFont(font).setFontSize(18).setMarginLeft(headingMargin);
            Style paragraphStyle =
                    new Style().setFont(font).setFontSize(12).setMarginLeft(paragraphMargin);

            Text headingText = new Text("Heading 1").addStyle(headingStyle);
            Paragraph heading = new Paragraph(headingText);
            layoutDoc.add(heading);

            for (int i = 0; i < numParagraphs; i++) {
                Paragraph p = new Paragraph(paragraphText).addStyle(paragraphStyle);
                layoutDoc.add(p);
            }

            String longText =
                    new StringBuilder()
                            .append("Paragraph with first line indent")
                            .append("Paragraph with first line indent")
                            .append(paragraphText)
                            .toString();
            for (int i = 0; i < 2; i++) {
                Paragraph p =
                        new Paragraph(longText)
                                .addStyle(paragraphStyle)
                                .setFirstLineIndent(paragraphIndent);
                layoutDoc.add(p);
            }
        }
        return pdfPath;
    }

    @Test
    void noIssuesWhenNoReferenceLeftEdgeExists() throws Exception {
        // 1 heading + 2 first-line-indent paragraphs = 3 P elements, all at same edge.
        // They form one run, but no non-run siblings exist → no reference → skipped.
        Path pdfFile = createTestPdfWithParagraphRun(0, "text", 0, 0, 0);
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            walkWith(pdfDoc, check);
        }

        assertTrue(check.getIssues().isEmpty(), "Should have no issues");
    }

    @Test
    void detectsIndentedParagraphRun() throws Exception {
        // Heading at page margin (~36pt), paragraphs indented 30pt further right (~66pt).
        // 5 + 2 = 7 indented P elements form a sub-run; heading serves as reference.
        // indent = 30pt > 10pt threshold → detected.
        String paragraphText = "These paragraphs are indented relative to the heading.";
        Path pdfFile = createTestPdfWithParagraphRun(5, paragraphText, 0, 30, 0);
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            walkWith(pdfDoc, check);
        }

        assertEquals(1, check.getIssues().size(), "Should have 1 issue");
        Issue issue = check.getIssues().get(0);
        assertEquals(IssueType.LIST_TAGGED_AS_PARAGRAPHS, issue.type());
    }

    @Test
    void noIssuesWhenParagraphsNotIndented() throws Exception {
        // Heading at margin 30pt (~66pt), paragraphs at margin 0pt (~36pt).
        // Paragraphs are LESS indented than heading → negative indent → not detected.
        String paragraphText = "These paragraphs are not indented relative to the heading.";
        Path pdfFile = createTestPdfWithParagraphRun(5, paragraphText, 30, 0, 0);
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            walkWith(pdfDoc, check);
        }

        assertTrue(
                check.getIssues().isEmpty(),
                "Should have no issues when paragraphs are not indented");
    }

    // == Link evidence ===================================================

    @Test
    void detectsAndFixesParagraphOfLinks() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            Document layoutDoc = new Document(pdfDoc);
            Paragraph p = new Paragraph();
            for (int i = 0; i < 5; i++) {
                Link link = new Link("Link " + i, PdfAction.createURI("https://uw.edu/" + i));
                p.add(link);
            }
            layoutDoc.add(p);

            walkWith(pdfDoc, check);

            assertEquals(1, check.getIssues().size(), "Should have 1 issue");
            Issue issue = check.getIssues().get(0);
            assertEquals(
                    IssueType.PARAGRAPH_OF_LINKS,
                    issue.type(),
                    "Issue type should be PARAGRAPH_OF_LINKS");

            issue.fix().apply(new DocContext(pdfDoc));

            Node<String> roleTree =
                    StructTree.toRoleTree(StructTree.findDocument(pdfDoc.getStructTreeRoot()));
            assertEquals(
                    "Document[L[LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]]]]",
                    roleTree.toString());
            layoutDoc.close();
        }
    }

    @Test
    void ignoresParagraphsWithIntermixedLinks() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            for (int i = 0; i < 5; i++) {
                p.add(new Link("Link " + i, PdfAction.createURI("https://uw.edu")));
                p.add(new Text("Text between links"));
            }
            layoutDoc.add(p);
            assertEquals(
                    "Document[P[Link[],Span[],Link[],Span[],Link[],Span[],Link[],Span[],Link[],Span[]]]",
                    StructTree.toRoleTreeString(
                            StructTree.findDocument(pdfDoc.getStructTreeRoot())));

            walkWith(pdfDoc, check);
            assertEquals(0, check.getIssues().size(), "Should have 0 issues");
        }
    }

    @Test
    void ignoresParagraphOfLinksSharingOneDestination() throws Exception {
        // One logical link that the authoring tool split across two Link tags: both
        // annotations target the same destination, so this is a wrapped line, not a list.
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Program of Study: Major:", PdfAction.createURI(SHARED_URI)));
            p.add(new Link("Developmental and Youth Studies", PdfAction.createURI(SHARED_URI)));
            layoutDoc.add(p);

            walkWith(pdfDoc, check);

            assertTrue(check.getIssues().isEmpty(), "Should have no issues");
        }
    }

    @Test
    void detectsParagraphOfTwoLinksWithDistinctDestinations() throws Exception {
        // Two links to different targets is a genuine two-item list.
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Autumn 2024", PdfAction.createURI("https://uw.edu/AUT2024/acmpt")));
            p.add(new Link("Winter 2025", PdfAction.createURI("https://uw.edu/WIN2025/acmpt")));
            layoutDoc.add(p);

            walkWith(pdfDoc, check);

            assertEquals(1, check.getIssues().size(), "Should have 1 issue");
            assertEquals(IssueType.PARAGRAPH_OF_LINKS, check.getIssues().get(0).type());
        }
    }

    @Test
    void detectsParagraphOfLinksAgreeingOnActionButNotOnOriginalUri() throws Exception {
        // Web Capture rewrites a link's /A action to an internal /GoTo and preserves the
        // original URI, fragment included, in /PA. Two entries can share a target page
        // while pointing at different anchors, so /PA must outrank /A when comparing.
        MistaggedListCheck check = new MistaggedListCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Undergraduate Programs", PdfAction.createURI(SHARED_URI)));
            p.add(new Link("Graduate Programs", PdfAction.createURI(SHARED_URI)));
            layoutDoc.add(p);

            List<PdfAnnotation> annots = pdfDoc.getPage(1).getAnnotations();
            assertEquals(2, annots.size(), "Expected one annotation per link");
            setOriginalUri(annots.get(0), SHARED_URI + "#undergradPrograms");
            setOriginalUri(annots.get(1), SHARED_URI + "#gradPrograms");

            walkWith(pdfDoc, check);

            assertEquals(1, check.getIssues().size(), "Should have 1 issue");
            assertEquals(IssueType.PARAGRAPH_OF_LINKS, check.getIssues().get(0).type());
        }
    }

    /** Records a Web Capture original URI in an annotation's /PA entry. */
    private static void setOriginalUri(PdfAnnotation annot, String uri) {
        annot.getPdfObject().put(PdfName.PA, PdfAction.createURI(uri).getPdfObject());
    }
}
