// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later

package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.FoldIntoPreviousItemFix;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoSublistFix;
import net.boyechko.pdf.autoa11y.fixes.WrapBulletedRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MistaggedListCheckTest extends PdfTestBase {

    /**
     * Catalog pages whose paragraphs lump many bulleted items into one tag: P #43 covers seven
     * bullets over sixteen lines, P #69 and P #72 lump nine items across two LIs of one list, and P
     * #76 opens mid-item ahead of ten hollow sub-bullets. P #75 carries a single bullet.
     */
    private static final Path LUMPED_PDF = Path.of("src/test/resources/catalog_098-102.pdf");

    /**
     * A catalog page whose Part holds four bulleted P elements in a row (#48 through #51), each one
     * link, plus P #55, two lines deep with a single bullet on the first.
     */
    private static final Path LOOSE_PDF = Path.of("src/test/resources/catalog_006.pdf");

    /**
     * Catalog pages whose L #76 is tagged as two items but sets three: P #79 opens the first item
     * on page 1, and P #82 finishes it on page 2 before running two more items at that same indent.
     */
    private static final Path CONTINUED_PDF = Path.of("src/test/resources/catalog_110-111.pdf");

    /**
     * Catalog pages whose L #63 is tagged as two items but sets four: P #66 lumps two of them on
     * page 1, and P #69 finishes the second on page 2 before opening two more at that indent.
     */
    private static final Path CONTINUED_AFTER_LUMP_PDF =
            Path.of("src/test/resources/catalog_199-200.pdf");

    private static MistaggedListCheck checkOf(Path pdf) throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdf.toString()))) {
            walkWith(pdfDoc, check);
        }
        return check;
    }

    private static void walkWith(PdfDocument pdfDoc, MistaggedListCheck check) throws Exception {
        StructTreeWalker walker = new StructTreeWalker();
        walker.addVisitor(check);
        walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }

    /** Maps object number to issue message for every issue of the given type. */
    private static Map<Integer, String> issuesByObjNum(MistaggedListCheck check, IssueType type) {
        return check.getIssues().stream()
                .filter(issue -> issue.type() == type)
                .collect(
                        Collectors.toMap(
                                issue -> issue.where().objNum(),
                                Issue::message,
                                (first, second) -> first,
                                LinkedHashMap::new));
    }

    // == Lumped items: one element covering several bullets ==============

    @ParameterizedTest
    @CsvSource({"Note, false", "Link, false", "Span, false", "Em, true", "Strong, true"})
    void lumpedItemsBelongOnlyToRolesNotClassifiedAsInline(String role, boolean reported)
            throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LUMPED_PDF.toString()),
                        new PdfWriter(testOutputStream(role + ".pdf")))) {
            elementByObjNum(pdfDoc, 43).setRole(RoleMap.toPdfName(role));

            walkWith(pdfDoc, check);

            assertEquals(
                    reported, issuesByObjNum(check, IssueType.LIST_ITEMS_LUMPED).containsKey(43));
        }
    }

    @Test
    void paragraphOwnsLumpedItemsWrappedInAnInlineNote() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LUMPED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            PdfStructElem paragraph = elementByObjNum(pdfDoc, 43);
            var kids = List.copyOf(StructTree.kidsOf(paragraph));
            PdfStructElem note = new PdfStructElem(pdfDoc, PdfName.Note);
            paragraph.addKid(note);
            for (var kid : kids) {
                StructTree.moveKid(kid, paragraph, note);
            }

            walkWith(pdfDoc, check);

            Map<Integer, String> lumped = issuesByObjNum(check, IssueType.LIST_ITEMS_LUMPED);
            assertTrue(lumped.containsKey(43));
            assertFalse(lumped.containsKey(StructTree.objNum(note)));
            Issue issue =
                    check.getIssues().stream()
                            .filter(i -> i.type() == IssueType.LIST_ITEMS_LUMPED)
                            .filter(i -> Integer.valueOf(43).equals(i.where().objNum()))
                            .findFirst()
                            .orElseThrow();
            assertNull(issue.fix(), "Inline markup requires manual review");
        }
    }

    @Test
    void derivesItemLineCountsFromBulletSpacing() throws Exception {
        // P #43's sixteen lines carry seven bullets, so its items wrap 2, 3, 1, 3, 3, 2 and 2
        // lines — a spec no whole-element comparison could recover.
        Map<Integer, String> lumped =
                issuesByObjNum(checkOf(LUMPED_PDF), IssueType.LIST_ITEMS_LUMPED);

        assertTrue(lumped.containsKey(43), "P #43 should be reported: " + lumped);
        assertTrue(lumped.get(43).contains("2,3,1,3,3,2,2"), lumped.get(43));
        assertTrue(lumped.get(43).contains("7 bullet glyphs"), lumped.get(43));
    }

    @Test
    void reportsLumpedItemsInsideAWellFormedList() throws Exception {
        // P #69 and P #72 are the sole paragraphs of two LIs in one list, yet between them
        // they lump nine items; the list looks well-formed from the structure alone.
        Map<Integer, String> lumped =
                issuesByObjNum(checkOf(LUMPED_PDF), IssueType.LIST_ITEMS_LUMPED);

        assertTrue(lumped.containsKey(69), "P #69 should be reported: " + lumped);
        assertTrue(lumped.get(69).contains("1,1,1,1,1,1"), lumped.get(69));
        assertTrue(lumped.containsKey(72), "P #72 should be reported: " + lumped);
        assertTrue(lumped.get(72).contains("1,2,1"), lumped.get(72));
    }

    @Test
    void foldsLeadingLinesIntoPreviousItemAndNestsTheRest() throws Exception {
        // P #76 opens with a line finishing the item P #75 began, then runs ten hollow
        // sub-bullets. Splitting on the bullets alone would hand that line to the wrong item.
        Issue issue =
                checkOf(LUMPED_PDF).getIssues().stream()
                        .filter(i -> i.type() == IssueType.LIST_ITEMS_LUMPED)
                        .filter(i -> Integer.valueOf(76).equals(i.where().objNum()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("P #76 not reported"));

        assertTrue(issue.message().contains("10 bullet glyphs"), issue.message());
        assertInstanceOf(SplitIntoSublistFix.class, issue.fix());
    }

    @Test
    void rebuiltItemJoinsTheListAlreadyTaggedAfterIt() throws Exception {
        // P #75's item and L #77's items share a bullet indent, so remediation must end with
        // one three-item list — the rebuilt item, then L #77's two — not two lists in a row.
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LUMPED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, check);
            check.getIssues().applyFixes(new DocContext(pdfDoc));

            assertEquals(
                    "L[LI[LBody[P[],P[],L[LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],"
                            + "LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],"
                            + "LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]]]]],"
                            + "LI[LBody[P[]]],LI[LBody[P[]]]]",
                    StructTree.toRoleTreeString(elementByObjNum(pdfDoc, 77)),
                    "one list of three items, the first carrying the ten-item sublist");
        }
    }

    @Test
    void foldsLeadingLinesIntoThePreviousItemOfTheSameList() throws Exception {
        // P #82's bullets sit at P #79's own indent, so its opening line finishes #79's item
        // and the rest open the list's next items rather than a sublist of the one it continues.
        Issue issue =
                checkOf(CONTINUED_PDF).getIssues().stream()
                        .filter(i -> i.type() == IssueType.LIST_ITEMS_LUMPED)
                        .filter(i -> Integer.valueOf(82).equals(i.where().objNum()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("P #82 not reported"));

        assertTrue(issue.message().contains("2 bullet glyphs"), issue.message());
        assertTrue(issue.message().contains("3,3"), issue.message());
        assertInstanceOf(FoldIntoPreviousItemFix.class, issue.fix());
    }

    @Test
    void continuedListEndsWithTheItemsItsBulletsPromise() throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(CONTINUED_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, check);
            check.getIssues().applyFixes(new DocContext(pdfDoc));

            assertEquals(
                    "L[LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]]]",
                    StructTree.toRoleTreeString(elementByObjNum(pdfDoc, 76)),
                    "the two tagged items become the three the bullets promise");
        }
    }

    @Test
    void foldsIntoTheItemTheLumpAheadOfItWasSplitInto() throws Exception {
        // The item P #69 continues does not exist until P #66's lump is split, and the fold must
        // land in the last of the items it becomes, not the element the lump started as.
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(CONTINUED_AFTER_LUMP_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, check);
            check.getIssues().applyFixes(new DocContext(pdfDoc));

            PdfStructElem list = elementByObjNum(pdfDoc, 63);
            List<PdfStructElem> items =
                    StructTree.childrenOf(list, PdfStructElem.class).stream()
                            .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                            .toList();
            assertEquals(4, items.size(), "one item per bullet");
            String second = itemText(pdfDoc, items.get(1));
            assertTrue(second.startsWith("Competently access"), second);
            assertTrue(second.endsWith("populations."), second);
        }
    }

    @Test
    void ignoresElementCoveringASingleBullet() throws Exception {
        // P #52, P #75 and P #89 are each one bulleted line: correctly one item apiece.
        Map<Integer, String> lumped =
                issuesByObjNum(checkOf(LUMPED_PDF), IssueType.LIST_ITEMS_LUMPED);

        assertFalse(lumped.containsKey(52), "P #52 covers one bullet: " + lumped);
        assertFalse(lumped.containsKey(75), "P #75 covers one bullet: " + lumped);
        assertFalse(lumped.containsKey(89), "P #89 covers one bullet: " + lumped);
    }

    @Test
    void ignoresMultiLineElementWithNoBullets() throws Exception {
        // P #93's twelve lines and P #114's nine carry no bullets at all: plain prose.
        Map<Integer, String> lumped =
                issuesByObjNum(checkOf(LUMPED_PDF), IssueType.LIST_ITEMS_LUMPED);

        assertFalse(lumped.containsKey(93), "P #93 has no bullets: " + lumped);
        assertFalse(lumped.containsKey(114), "P #114 has no bullets: " + lumped);
    }

    @Test
    void doesNotCountOneBulletTwiceWhenItsLineIsSetInSeveralRuns() throws Exception {
        // P #55 and P #56 each set their bulleted line in two runs of marked content, the Link
        // and the text after it. That is one line carrying one bullet, so neither lumps items.
        Map<Integer, String> lumped =
                issuesByObjNum(checkOf(LOOSE_PDF), IssueType.LIST_ITEMS_LUMPED);

        assertFalse(lumped.containsKey(55), "P #55 carries one bullet: " + lumped);
        assertFalse(lumped.containsKey(56), "P #56 carries one bullet: " + lumped);
    }

    // == Loose items: several siblings each covering one bullet ==========

    @ParameterizedTest
    @ValueSource(strings = {"Div", "L", "Table"})
    void groupingListAndTableRolesDoNotBecomeLooseItems(String role) throws Exception {
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LOOSE_PDF.toString()),
                        new PdfWriter(testOutputStream(role + ".pdf")))) {
            for (int objNum = 48; objNum <= 51; objNum++) {
                elementByObjNum(pdfDoc, objNum).setRole(RoleMap.toPdfName(role));
            }

            walkWith(pdfDoc, check);
            check.getIssues().applyFixes(new DocContext(pdfDoc));

            for (int objNum = 48; objNum <= 51; objNum++) {
                assertEquals(
                        44,
                        StructTree.objNum(StructTree.parentOf(elementByObjNum(pdfDoc, objNum))));
            }
        }
    }

    @Test
    void wrapsConsecutiveBulletedSiblingsInOneList() throws Exception {
        // P #48 through P #51 each carry one bullet at the same indent under Part #44, and
        // each holds the single Link that is its item's text.
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LOOSE_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, check);
            check.getIssues().applyFixes(new DocContext(pdfDoc));

            PdfStructElem wrapped = parentOfRole(pdfDoc, 48, "LBody");
            assertEquals(
                    "L[LI[LBody[P[Link[]]]],LI[LBody[P[Link[]]]],LI[LBody[P[Link[]]]],"
                            + "LI[LBody[P[Link[]]]]]",
                    StructTree.toRoleTreeString(listAncestorOf(wrapped)),
                    "the four bulleted paragraphs become one four-item list");
        }
    }

    @Test
    void doesNotWrapALoneBulletedSiblingAsAOneItemList() throws Exception {
        // P #75 carries a single bullet, so a pass counting bullets against whole elements
        // would wrap it alone. Its item runs on into P #76, which claims it, and one item is
        // not a list in any case.
        assertTrue(
                checkOf(LUMPED_PDF).getIssues().stream()
                        .noneMatch(i -> i.fix() instanceof WrapBulletedRunInList),
                "no lone bullet should become a list");
    }

    @Test
    void carvesABulletedTailOutOfAProseParagraphToOpenARun() throws Exception {
        // P #55 sets a line of prose and then the run's first item; P #56 sets the second. The
        // item is carved out of P #55, which keeps its prose, and the two become one list.
        MistaggedListCheck check = new MistaggedListCheck();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LOOSE_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, check);
            check.getIssues().applyFixes(new DocContext(pdfDoc));

            assertEquals(
                    "P[Link[]]",
                    StructTree.toRoleTreeString(elementByObjNum(pdfDoc, 55)),
                    "P #55 keeps the prose the carve left behind");
            assertEquals(
                    "L[LI[LBody[P[Link[]]]],LI[LBody[P[Link[]]]]]",
                    StructTree.toRoleTreeString(nextSiblingOf(pdfDoc, 55)),
                    "the carved item and P #56 become one two-item list");
        }
    }

    // == Helpers =========================================================

    /** Concatenates the raw text of every MCR under an element, in reading order. */
    private static String itemText(PdfDocument doc, PdfStructElem elem) {
        StringBuilder text = new StringBuilder();
        for (PdfMcr mcr : StructTree.descendantsOf(elem, PdfMcr.class)) {
            int pageNum = StructTree.pageOf(mcr);
            Content.McidContent content =
                    Content.extractContentForPage(doc.getPage(pageNum)).get(mcr.getMcid());
            if (content != null) {
                content.spans().forEach(span -> text.append(span.text()));
            }
        }
        return text.toString().strip();
    }

    /** Returns the element following the given one among its parent's children. */
    private static PdfStructElem nextSiblingOf(PdfDocument doc, int objNum) {
        PdfStructElem elem = elementByObjNum(doc, objNum);
        List<PdfStructElem> siblings =
                StructTree.childrenOf(
                        (PdfStructElem) StructTree.parentOf(elem), PdfStructElem.class);
        for (int i = 0; i < siblings.size() - 1; i++) {
            if (StructTree.isSameElement(siblings.get(i), elem)) {
                return siblings.get(i + 1);
            }
        }
        throw new AssertionError("no sibling after #" + objNum);
    }

    /** Returns the nearest ancestor of the element with the given role. */
    private static PdfStructElem parentOfRole(PdfDocument doc, int objNum, String role) {
        PdfStructElem elem = elementByObjNum(doc, objNum);
        while (elem != null && !role.equals(StructTree.mappedRole(elem))) {
            elem = StructTree.parentOf(elem) instanceof PdfStructElem p ? p : null;
        }
        return elem;
    }

    /** Walks up from an LBody to the L that owns it. */
    private static PdfStructElem listAncestorOf(PdfStructElem lBody) {
        PdfStructElem elem = lBody;
        while (elem != null && !"L".equals(StructTree.mappedRole(elem))) {
            elem = StructTree.parentOf(elem) instanceof PdfStructElem p ? p : null;
        }
        return elem;
    }

    /** Finds a structure element by object number anywhere under the Document. */
    private static PdfStructElem elementByObjNum(PdfDocument doc, int objNum) {
        return allElements(StructTree.findDocument(doc.getStructTreeRoot())).stream()
                .filter(elem -> StructTree.objNum(elem) == objNum)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no element #" + objNum));
    }

    private static List<PdfStructElem> allElements(PdfStructElem root) {
        List<PdfStructElem> all = new ArrayList<>();
        all.add(root);
        StructTree.childrenOf(root, PdfStructElem.class)
                .forEach(kid -> all.addAll(allElements(kid)));
        return all;
    }
}
