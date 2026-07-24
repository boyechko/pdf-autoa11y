// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class SplitIntoListItemsFixTest extends PdfTestBase {

    /**
     * Catalog pages with lumped listings: bare paragraphs, nested sublists holding lumped MCRs
     * (page 2's nine-line ELCBUS listing), a sublist item whose two MCRs straddle the page 2-3
     * boundary, and further bare lumped paragraphs on pages 4 and 7.
     */
    private static final Path CATALOG_PDF = Path.of("src/test/resources/catalog_038-044.pdf");

    @Test
    void splitsLumpedListItemIntoIndividualItems() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 2, 16);
            PdfStructElem list = grandListOf(p);

            new SplitIntoListItemsFix(p, 9).apply(ctx);

            List<PdfStructElem> items = listItems(list);
            assertEquals(9, items.size(), "8 new LIs joined the original 1");

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(2));
            List<String> texts = items.stream().map(li -> itemText(li, content)).toList();
            assertTrue(texts.get(0).startsWith("ELCBUS 300"), texts.get(0));
            assertTrue(texts.get(1).startsWith("ELCBUS 301"), texts.get(1));
            assertTrue(texts.get(4).startsWith("ELCBUS 330"), texts.get(4));
            assertTrue(texts.get(8).startsWith("ELCBUS 382"), texts.get(8));

            assertProperlyNestedOperators(doc.getPage(2).getContentBytes());
        }
    }

    @Test
    void derivesItemCountWhenNoneGiven() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 3, 10);
            PdfStructElem list = grandListOf(p);

            new ScribbledInstructionFix(p, "!SPLIT_LINES").apply(ctx);

            assertEquals(2, listItems(list).size(), "1 new LI joined the original 1");
        }
    }

    @Test
    void refusesWhenLineCountDoesNotMatch() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 2, 16);
            PdfStructElem list = grandListOf(p);

            assertThrows(
                    IllegalStateException.class, () -> new SplitIntoListItemsFix(p, 5).apply(ctx));

            assertEquals(1, listItems(list).size(), "structure untouched on refusal");
        }
    }

    @Test
    void wrapsBareParagraphIntoNewList() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 1, 9);
            PdfStructElem container = (PdfStructElem) p.getParent();
            int origIndex = StructTree.findKidIndex(container, p);

            new SplitIntoListItemsFix(p, 6).apply(ctx);

            PdfStructElem list = (PdfStructElem) container.getKids().get(origIndex);
            assertEquals("L", list.getRole().getValue(), "P replaced by a new L at its position");
            List<PdfStructElem> items = listItems(list);
            assertEquals(6, items.size());

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(1));
            assertTrue(itemText(items.get(1), content).startsWith("B BUS 307"));
        }
    }

    @Test
    void splitItemsJoinListBeforeLaterSiblingItems() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 2, 21);
            PdfStructElem list = grandListOf(p);
            List<PdfStructElem> origItems = listItems(list);
            PdfStructElem origLastItem = origItems.get(origItems.size() - 1);

            new SplitIntoListItemsFix(p, 2).apply(ctx);

            List<PdfStructElem> items = listItems(list);
            assertEquals(origItems.size() + 1, items.size(), "1 new LI joined the list");
            assertSame(
                    origLastItem.getPdfObject(),
                    items.get(items.size() - 1).getPdfObject(),
                    "new items are inserted before the later sibling items, not appended");

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(2));
            assertTrue(itemText(items.get(2), content).startsWith("Capstone"));
            assertTrue(itemText(items.get(3), content).startsWith("ELCBUS 470"));
        }
    }

    @Test
    void appendsContinuationMcrToPrecedingItem() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 1, 3);
            PdfStructElem container = (PdfStructElem) p.getParent();
            int origIndex = StructTree.findKidIndex(container, p);

            new SplitIntoListItemsFix(p, "1,1,1,1,1,2").apply(ctx);

            PdfStructElem list = (PdfStructElem) container.getKids().get(origIndex);
            List<PdfStructElem> items = listItems(list);
            assertEquals(6, items.size(), "the two-line item adds no extra item");

            PdfStructElem lastItem = items.get(5);
            assertEquals(
                    2,
                    StructTree.descendantsOf(lastItem, PdfMcr.class).size(),
                    "last item holds one MCR fragment per side of the MCR boundary");

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(1));
            String lastText = itemText(lastItem, content);
            assertTrue(lastText.startsWith("B BUS 497"), lastText);
            assertTrue(lastText.contains("Undergraduate Research"), lastText);
            assertTrue(itemText(items.get(3), content).startsWith("B BUS 486"));
        }
    }

    @Test
    void splitsElementWhoseMcrsSpanPages() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 2, 15);
            PdfStructElem list = grandListOf(p);

            new SplitIntoListItemsFix(p, "1,1,1,1,2").apply(ctx);

            List<PdfStructElem> items = listItems(list);
            assertEquals(5, items.size(), "6 lines across two pages grouped into 5 items");

            List<Integer> itemPages =
                    items.stream()
                            .map(li -> StructTree.descendantsOf(li, PdfMcr.class).get(0))
                            .map(StructTree::pageOf)
                            .toList();
            assertEquals(List.of(2, 2, 3, 3, 3), itemPages);

            Map<Integer, Content.McidContent> page2 = Content.extractContentForPage(doc.getPage(2));
            Map<Integer, Content.McidContent> page3 = Content.extractContentForPage(doc.getPage(3));
            assertTrue(itemText(items.get(0), page2).startsWith("B BUS/ELCBUS 482"));
            assertTrue(itemText(items.get(1), page2).startsWith("B BUS/ELCBUS 483"));
            assertTrue(itemText(items.get(2), page3).startsWith("B BUS 441"));
            assertTrue(itemText(items.get(3), page3).startsWith("B BUS/ELCBUS 487"));
            String lastText = itemText(items.get(4), page3);
            assertTrue(lastText.startsWith("B BUS/ELCBUS 497"), lastText);
            assertTrue(lastText.contains("Undergraduate"), lastText);

            // Page 3's block opens its BDC outside the text objects; splicing must not
            // leave BT/ET and BDC/EMC pairs interleaved (Acrobat: "Unbalanced operators").
            assertProperlyNestedOperators(doc.getPage(3).getContentBytes());
        }
    }

    @Test
    void keepsMultiLineItemAsSingleUnsplitMcr() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = elementOwningMcid(doc, 2, 16);
            PdfStructElem list = grandListOf(p);

            new SplitIntoListItemsFix(p, "1,1,2,1,1,1,1,1").apply(ctx);

            List<PdfStructElem> items = listItems(list);
            assertEquals(8, items.size(), "7 new LIs joined the original 1");

            PdfStructElem twoLineItem = items.get(2);
            assertEquals(
                    1,
                    StructTree.descendantsOf(twoLineItem, PdfMcr.class).size(),
                    "consecutive same-item lines stay one unsplit MCR");

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(2));
            String text = itemText(twoLineItem, content);
            assertTrue(text.startsWith("ELCBUS 310"), text);
            assertTrue(text.contains("ELCBUS 320"), text);
            assertTrue(itemText(items.get(3), content).startsWith("ELCBUS 330"));
        }
    }

    /**
     * Asserts that BT/ET and BDC/BMC/EMC pairs are balanced and properly nested — a marked-content
     * block must open and close on the same side of a text-object boundary.
     */
    private static void assertProperlyNestedOperators(byte[] content) throws Exception {
        Deque<String> stack = new ArrayDeque<>();
        try (PdfTokenizer tokenizer =
                new PdfTokenizer(
                        new RandomAccessFileOrArray(
                                new RandomAccessSourceFactory().createSource(content)))) {
            while (tokenizer.nextToken()) {
                if (tokenizer.getTokenType() != PdfTokenizer.TokenType.Other) {
                    continue;
                }
                switch (tokenizer.getStringValue()) {
                    case "BT" -> stack.push("BT");
                    case "BDC", "BMC" -> stack.push("MC");
                    case "ET" -> assertEquals("BT", stack.poll(), "ET must close the open BT");
                    case "EMC" -> assertEquals("MC", stack.poll(), "EMC must close the open BDC");
                    default -> {}
                }
            }
        }
        assertTrue(stack.isEmpty(), "unclosed operators: " + stack);
    }

    private PdfDocument openForStamping(Path input) throws Exception {
        return new PdfDocument(new PdfReader(input.toString()), new PdfWriter(testOutputStream()));
    }

    /** Finds the struct element owning the MCR with the given MCID on the given page. */
    private static PdfStructElem elementOwningMcid(PdfDocument doc, int pageNum, int mcid) {
        PdfStructElem document = StructTree.findDocument(doc.getStructTreeRoot());
        return StructTree.descendantsOf(document, PdfMcr.class).stream()
                .filter(mcr -> mcr.getMcid() == mcid && StructTree.pageOf(mcr) == pageNum)
                .map(mcr -> (PdfStructElem) mcr.getParent())
                .findFirst()
                .orElseThrow();
    }

    /** Returns the L element two levels above a lumped P (P > LBody > LI > L). */
    private static PdfStructElem grandListOf(PdfStructElem p) {
        PdfStructElem lBody = (PdfStructElem) p.getParent();
        PdfStructElem li = (PdfStructElem) lBody.getParent();
        return (PdfStructElem) li.getParent();
    }

    private static List<PdfStructElem> listItems(PdfStructElem list) {
        return StructTree.childrenOf(list, PdfStructElem.class).stream()
                .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                .toList();
    }

    /**
     * Extracts the raw text of all MCRs under an LI. Joins the raw spans because McidContent.text()
     * may falsely strip spaces from short lines whose single-character word ratio trips the
     * artificial-spacing heuristic (e.g. "B BUS 307 - Business Writing").
     */
    private static String itemText(PdfStructElem li, Map<Integer, Content.McidContent> content) {
        return StructTree.descendantsOf(li, PdfMcr.class).stream()
                .map(mcr -> content.get(mcr.getMcid()))
                .filter(Objects::nonNull)
                .flatMap(mc -> mc.spans().stream())
                .map(Content.TextSpan::text)
                .collect(joining())
                .strip();
    }
}
