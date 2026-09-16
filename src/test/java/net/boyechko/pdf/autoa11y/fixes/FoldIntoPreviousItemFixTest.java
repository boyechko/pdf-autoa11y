// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class FoldIntoPreviousItemFixTest extends PdfTestBase {

    /**
     * Catalog pages whose L #76 is tagged as two items but sets three: P #79 opens the first item
     * on page 1, and P #82 finishes it on page 2 before running two more bulleted items at the same
     * indent.
     */
    private static final Path CATALOG_PDF = Path.of("src/test/resources/catalog_110-111.pdf");

    /**
     * Catalog pages whose L #63 is tagged as two items but sets four: P #66 lumps two of them on
     * page 1, and P #69 finishes the second on page 2 before opening two more at that indent.
     */
    private static final Path LUMPED_OPENER_PDF = Path.of("src/test/resources/catalog_199-200.pdf");

    private static final String TWO_THREE_LINE_ITEMS = "3,3";

    @Test
    void foldsTheContinuationLineIntoThePreviousItemAndSplitsTheRest() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem target = elementOwningMcid(doc, 1, 27);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);
            PdfStructElem list = listAncestorOf(element);

            new FoldIntoPreviousItemFix(element, 1, TWO_THREE_LINE_ITEMS).apply(ctx);

            assertEquals(
                    "L[LI[LBody[P[]]],LI[LBody[P[]]],LI[LBody[P[]]]]",
                    StructTree.toRoleTreeString(list),
                    "the two tagged items become the three the bullets promise");
        }
    }

    @Test
    void theContinuationLineLandsInTheOpeningItemsOwnParagraph() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem target = elementOwningMcid(doc, 1, 27);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);

            new FoldIntoPreviousItemFix(element, 1, TWO_THREE_LINE_ITEMS).apply(ctx);

            List<PdfMcr> mcrs = StructTree.descendantsOf(target, PdfMcr.class);
            assertEquals(2, mcrs.size(), "the item reads as one paragraph over two pages");
            assertEquals(1, StructTree.pageOf(mcrs.get(0)));
            assertEquals(2, StructTree.pageOf(mcrs.get(1)), "the folded line keeps its own page");
        }
    }

    @Test
    void emptiedItemIsRemovedFromTheList() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem target = elementOwningMcid(doc, 1, 27);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);
            PdfStructElem emptied = (PdfStructElem) element.getParent().getParent();
            PdfStructElem list = listAncestorOf(element);

            new FoldIntoPreviousItemFix(element, 1, TWO_THREE_LINE_ITEMS).apply(ctx);

            assertTrue(
                    StructTree.childrenOf(list, PdfStructElem.class).stream()
                            .noneMatch(kid -> StructTree.isSameElement(kid, emptied)),
                    "the item the continuation vacated is gone, not left empty");
        }
    }

    @Test
    void stampsTheFinalItemCountOnTheList() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem target = elementOwningMcid(doc, 1, 27);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);
            PdfStructElem list = listAncestorOf(element);

            new FoldIntoPreviousItemFix(element, 1, TWO_THREE_LINE_ITEMS).apply(ctx);

            assertTrue(
                    StructTree.getScribble(list).value().contains(ListItemScribble.countSegment(3)),
                    "the count reflects the list after the fold, not mid-split");
        }
    }

    @Test
    void recordsNoEventOfItsOwnOnTheList() throws Exception {
        // The fold moves content between items; what happened to the list itself is the split.
        // An event here would be read as the list having been folded into something.
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);
            PdfStructElem list = listAncestorOf(element);

            new FoldIntoPreviousItemFix(element, 1, TWO_THREE_LINE_ITEMS).apply(ctx);

            List<String> segments = StructTree.getScribble(list).segments();
            assertEquals(2, segments.size(), "only the split's event and the count: " + segments);
            assertEquals(ListItemScribble.countSegment(3), segments.get(1));
        }
    }

    @Test
    void refusesToFoldIntoAnElementOfAnotherRole() throws Exception {
        try (PdfDocument doc = openForStamping(CATALOG_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem target = elementOwningMcid(doc, 1, 27);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);
            target.setRole(RoleMap.toPdfName("H4"));

            assertThrows(
                    IllegalStateException.class,
                    () -> new FoldIntoPreviousItemFix(element, 1, TWO_THREE_LINE_ITEMS).apply(ctx));
        }
    }

    @Test
    void resolvesTheOpenerAgainstTheItemsALumpAheadOfItBecomes() throws Exception {
        // The item ahead lumps two of its own, so the lines being folded belong to the second
        // of the items it is split into — an element the tree did not hold at detection time.
        try (PdfDocument doc = openForStamping(LUMPED_OPENER_PDF)) {
            PdfStructElem lumped = elementOwningMcid(doc, 1, 23);
            PdfStructElem element = elementOwningMcid(doc, 2, 0);
            assertTrue(
                    StructTree.isSameElement(lumped, FoldIntoPreviousItemFix.openerFor(element)),
                    "the lump is the only item ahead of this element before it is split");

            new SplitIntoListItemsFix(lumped, "2,2").apply(new DocContext(doc));

            PdfStructElem opener = FoldIntoPreviousItemFix.openerFor(element);
            assertNotNull(opener);
            assertFalse(
                    StructTree.isSameElement(lumped, opener),
                    "the opener is the item the split created, not the lump it came from");
        }
    }

    // == Helpers =========================================================

    private PdfDocument openForStamping(Path input) throws Exception {
        return new PdfDocument(new PdfReader(input.toString()), new PdfWriter(testOutputStream()));
    }

    /** Walks up from an element to the L that owns it. */
    private static PdfStructElem listAncestorOf(PdfStructElem element) {
        PdfStructElem elem = element;
        while (elem != null && !"L".equals(StructTree.mappedRole(elem))) {
            elem = StructTree.parentOf(elem) instanceof PdfStructElem p ? p : null;
        }
        return elem;
    }

    private static PdfStructElem elementOwningMcid(PdfDocument doc, int pageNum, int mcid) {
        PdfStructElem document = StructTree.findDocument(doc.getStructTreeRoot());
        return StructTree.descendantsOf(document, PdfMcr.class).stream()
                .filter(mcr -> mcr.getMcid() == mcid && StructTree.pageOf(mcr) == pageNum)
                .map(mcr -> (PdfStructElem) mcr.getParent())
                .findFirst()
                .orElseThrow();
    }
}
