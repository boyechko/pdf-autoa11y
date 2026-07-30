// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class SplitMarkedContentFixTest extends PdfTestBase {

    /**
     * A catalog cover page whose page-1 headings lump differently-sized lines into one
     * marked-content block: an H2 (MCID 15) holding "Graduate Programs" at 19.5 and "Computing and
     * Software Systems" at 21.75, and a P (MCID 16) holding a two-line title wrapped at 15 followed
     * by "Program Overview" at 12.75.
     */
    private static final Path CATALOG_067 = Path.of("src/test/resources/catalog_067.pdf");

    @Test
    void splitsTwoDifferentlySizedLinesIntoSeparateSameRoleSiblings() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_067)) {
            PdfStructElem heading = elementOwningMcid(doc, 1, 15);
            PdfStructElem parent = (PdfStructElem) heading.getParent();
            int before = sameRoleSiblingCount(parent, heading);

            new SplitMarkedContentFix(heading).apply(new DocContext(doc));

            assertEquals(
                    before + 1,
                    sameRoleSiblingCount(parent, heading),
                    "the second font run becomes a new same-role sibling");

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(1));
            List<PdfStructElem> runs = sameRoleSiblingsAfter(parent, heading, 2);
            assertTrue(text(runs.get(0), content).startsWith("Graduate Programs"), "run 0");
            assertTrue(
                    text(runs.get(1), content).startsWith("Computing and Software Systems"),
                    "run 1");

            assertProperlyNestedOperators(doc.getPage(1).getContentBytes());
        }
    }

    @Test
    void keepsASameSizeWrapTogetherInTheFirstRun() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_067)) {
            PdfStructElem paragraph = elementOwningMcid(doc, 1, 16);
            PdfStructElem parent = (PdfStructElem) paragraph.getParent();

            new SplitMarkedContentFix(paragraph).apply(new DocContext(doc));

            Map<Integer, Content.McidContent> content =
                    Content.extractContentForPage(doc.getPage(1));
            List<PdfStructElem> runs = sameRoleSiblingsAfter(parent, paragraph, 2);
            String firstRun = text(runs.get(0), content);
            assertTrue(firstRun.startsWith("Program of Study:"), firstRun);
            assertTrue(
                    firstRun.contains("Development"),
                    "the same-size wrap stays in run 0: " + firstRun);
            assertTrue(text(runs.get(1), content).startsWith("Program Overview"), "run 1");

            assertProperlyNestedOperators(doc.getPage(1).getContentBytes());
        }
    }

    @Test
    void stampsEveryResultingElementWithASplitScribble() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_067)) {
            PdfStructElem heading = elementOwningMcid(doc, 1, 15);
            PdfStructElem parent = (PdfStructElem) heading.getParent();

            new SplitMarkedContentFix(heading).apply(new DocContext(doc));

            for (PdfStructElem run : sameRoleSiblingsAfter(parent, heading, 2)) {
                var scribble = StructTree.getScribble(run);
                assertTrue(scribble != null && scribble.toolAuthored(), "tool-authored scribble");
                assertTrue(
                        scribble.segments().stream()
                                .anyMatch(s -> s.trim().equals("CONTENT SPLIT")),
                        "carries the split segment");
            }
        }
    }

    @Test
    void reapplyingIsANoOp() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_067)) {
            PdfStructElem heading = elementOwningMcid(doc, 1, 15);
            PdfStructElem parent = (PdfStructElem) heading.getParent();
            DocContext ctx = new DocContext(doc);

            new SplitMarkedContentFix(heading).apply(ctx);
            int afterFirst = sameRoleSiblingCount(parent, heading);
            new SplitMarkedContentFix(heading).apply(ctx);

            assertEquals(
                    afterFirst,
                    sameRoleSiblingCount(parent, heading),
                    "a second application splits nothing further");
        }
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

    /** Counts the parent's kids sharing the element's role, including the element itself. */
    private static int sameRoleSiblingCount(PdfStructElem parent, PdfStructElem elem) {
        String role = StructTree.mappedRole(elem);
        return (int)
                StructTree.childrenOf(parent, PdfStructElem.class).stream()
                        .filter(kid -> role.equals(StructTree.mappedRole(kid)))
                        .count();
    }

    /** Returns the run at index and the {@code count} same-role siblings starting from it. */
    private static List<PdfStructElem> sameRoleSiblingsAfter(
            PdfStructElem parent, PdfStructElem elem, int count) {
        String role = StructTree.mappedRole(elem);
        int start = StructTree.findKidIndex(parent, elem);
        return StructTree.childrenOf(parent, PdfStructElem.class).stream()
                .skip(start)
                .filter(kid -> role.equals(StructTree.mappedRole(kid)))
                .limit(count)
                .toList();
    }

    /** Joins the raw text of all MCRs under an element. */
    private static String text(PdfStructElem elem, Map<Integer, Content.McidContent> content) {
        return StructTree.descendantsOf(elem, PdfMcr.class).stream()
                .map(mcr -> content.get(mcr.getMcid()))
                .filter(Objects::nonNull)
                .flatMap(mc -> mc.spans().stream())
                .map(Content.TextSpan::text)
                .collect(joining())
                .strip();
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
}
