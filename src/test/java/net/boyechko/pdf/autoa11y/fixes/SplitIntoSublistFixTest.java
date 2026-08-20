// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

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
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class SplitIntoSublistFixTest extends PdfTestBase {

    /**
     * Catalog pages where P #76 opens with a line finishing the item P #75 began, then runs ten
     * hollow sub-bullets belonging to that item.
     */
    private static final Path CATALOG_PDF = Path.of("src/test/resources/catalog_098-102.pdf");

    private static final String TEN_ONE_LINE_ITEMS = "1,1,1,1,1,1,1,1,1,1";

    @Test
    void foldsContinuationLineIntoPredecessorsItemAndNestsTheRest() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem opener = elementOwningMcid(doc, 3, 3);
            PdfStructElem continuation = elementOwningMcid(doc, 3, 4);

            new SplitIntoSublistFix(continuation, opener, 1, TEN_ONE_LINE_ITEMS).apply(ctx);

            PdfStructElem itemBody = (PdfStructElem) opener.getParent();
            assertEquals("LBody", StructTree.mappedRole(itemBody));
            assertEquals(
                    "LBody[P[],P[],L[LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],"
                            + "LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]],"
                            + "LI[LBody[P[]]],LI[LBody[P[]]]]]",
                    StructTree.toRoleTreeString(itemBody),
                    "the opener, its continuation, then the ten sub-bullets as a sublist");
            assertProperlyNestedOperators(doc.getPage(3).getContentBytes());
        }
    }

    @Test
    void wrapsABarePredecessorInItsOwnList() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem opener = elementOwningMcid(doc, 3, 3);
            PdfStructElem continuation = elementOwningMcid(doc, 3, 4);
            assertEquals("Art", StructTree.mappedRole(StructTree.parentOf(opener)));

            new SplitIntoSublistFix(continuation, opener, 1, TEN_ONE_LINE_ITEMS).apply(ctx);

            PdfStructElem li = (PdfStructElem) opener.getParent().getParent();
            PdfStructElem list = (PdfStructElem) li.getParent();
            assertEquals("LI", StructTree.mappedRole(li));
            assertEquals("L", StructTree.mappedRole(list));
            assertEquals("Art", StructTree.mappedRole(StructTree.parentOf(list)));
        }
    }

    @Test
    void refusesWhenNoLineIsLeftForThePredecessorsItem() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem opener = elementOwningMcid(doc, 3, 3);
            PdfStructElem continuation = elementOwningMcid(doc, 3, 4);

            assertThrows(
                    IllegalStateException.class,
                    () -> new SplitIntoSublistFix(continuation, opener, 0, "1,1").apply(ctx));
        }
    }

    // == Helpers =========================================================

    private PdfDocument openForStamping(Path input) throws Exception {
        return new PdfDocument(new PdfReader(input.toString()), new PdfWriter(testOutputStream()));
    }

    private static PdfStructElem elementOwningMcid(PdfDocument doc, int pageNum, int mcid) {
        PdfStructElem document = StructTree.findDocument(doc.getStructTreeRoot());
        return StructTree.descendantsOf(document, PdfMcr.class).stream()
                .filter(mcr -> mcr.getMcid() == mcid && StructTree.pageOf(mcr) == pageNum)
                .map(mcr -> (PdfStructElem) mcr.getParent())
                .findFirst()
                .orElseThrow();
    }

    /** Asserts every BT and BDC in the stream is closed by its own ET and EMC, in order. */
    private static void assertProperlyNestedOperators(byte[] contentBytes) throws Exception {
        PdfTokenizer tokenizer =
                new PdfTokenizer(
                        new RandomAccessFileOrArray(
                                new RandomAccessSourceFactory().createSource(contentBytes)));
        Deque<String> stack = new ArrayDeque<>();
        while (tokenizer.nextToken()) {
            if (tokenizer.getTokenType() == PdfTokenizer.TokenType.Other) {
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
