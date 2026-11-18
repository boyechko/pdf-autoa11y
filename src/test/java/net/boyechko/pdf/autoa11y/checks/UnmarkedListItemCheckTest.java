// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later

package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.fixes.ListItemScribble.Count;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import org.junit.jupiter.api.Test;

class UnmarkedListItemCheckTest extends PdfTestBase {

    /**
     * A catalog page with two-item L #30 whose items hold sublists L #34 (three items) and L #47
     * (one), and L #52 whose two items are a pair of headings and a paragraph. L #30 already
     * carries {@code ITEMS 2} from an earlier rebuild.
     */
    private static final Path NESTED_PDF = Path.of("src/test/resources/catalog_131.pdf");

    /** A catalog page whose L #42 sets its three bullets as text in each item's Lbl. */
    private static final Path LABELLED_PDF = Path.of("src/test/resources/catalog_067.pdf");

    /** A catalog page whose L #49 numbers its three items 1. to 3. in their Lbls. */
    private static final Path NUMBERED_PDF = Path.of("src/test/resources/catalog_110-111.pdf");

    private static void walkWith(PdfDocument pdfDoc, UnmarkedListItemCheck check) {
        StructTreeWalker walker = new StructTreeWalker();
        walker.addVisitor(check);
        walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }

    /** Walks the document and applies every fix the check proposes, as a run would. */
    private static UnmarkedListItemCheck runWithFixes(PdfDocument pdfDoc) throws Exception {
        UnmarkedListItemCheck check = new UnmarkedListItemCheck();
        DocContext ctx = new DocContext(pdfDoc);
        StructTreeWalker walker = new StructTreeWalker();
        walker.addVisitor(check);
        walker.walk(pdfDoc.getStructTreeRoot(), ctx);
        for (var issue : check.getIssues()) {
            issue.fix().apply(ctx);
        }
        return check;
    }

    private static String instructionOf(PdfDocument pdfDoc, int objNum) {
        return Tag.of(StructTree.findByObjNumber(pdfDoc.getStructTreeRoot(), objNum))
                .scribble()
                .instruction();
    }

    private static List<String> segmentsOf(PdfDocument pdfDoc, int objNum) {
        return Tag.of(StructTree.findByObjNumber(pdfDoc.getStructTreeRoot(), objNum))
                .scribble()
                .segments();
    }

    @Test
    void countsOnlyTheListsOwnItemsAndMarkersLeavingSublistsTheirOwn() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 30).contains(Count.of(2, 2).segment()));
            assertTrue(segmentsOf(pdfDoc, 34).contains(Count.of(3, 3).segment()));
            assertTrue(segmentsOf(pdfDoc, 47).contains(Count.of(1, 1).segment()));
        }
    }

    @Test
    void countsNoMarkersForItemsThatAreReallyHeadings() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 52).contains(Count.of(2, 0).segment()));
        }
    }

    @Test
    void countsBulletCharactersSetInItemLabels() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LABELLED_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 42).contains(Count.of(3, 3).segment()));
        }
    }

    @Test
    void countsNumbersSetInItemLabelsAsMarkers() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NUMBERED_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 49).contains(Count.of(3, 3).segment()));
        }
    }

    @Test
    void replacesTheItemCountAnEarlierFixLeftAndKeepsItsHistory() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertEquals(
                    List.of("30", "REBUILD outline", Count.of(2, 2).segment()),
                    segmentsOf(pdfDoc, 30));
        }
    }

    @Test
    void stampsTheCountsOnceWhenRunTwice() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertEquals(List.of("34", Count.of(3, 3).segment()), segmentsOf(pdfDoc, 34));
        }
    }

    @Test
    void reportsTheDocumentAsChanged() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            UnmarkedListItemCheck check = new UnmarkedListItemCheck();
            walkWith(pdfDoc, check);

            assertTrue(check.hasChangedDoc());
        }
    }

    // == Breaking out unmarked items =======================================

    @Test
    void marksItemsWithoutAMarkerForBreakingOut() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            UnmarkedListItemCheck check = runWithFixes(pdfDoc);

            assertEquals("!BREAK_OUT", instructionOf(pdfDoc, 53));
            assertEquals("!BREAK_OUT", instructionOf(pdfDoc, 57));
            assertEquals(
                    List.of(53, 57),
                    check.getIssues().stream().map(issue -> issue.where().objNum()).toList());
        }
    }

    @Test
    void leavesItemsWithADrawnBulletInTheirList() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            runWithFixes(pdfDoc);

            // LI #31 and #44 carry the outer bullets; #35 is an item of sublist L #34.
            assertNull(instructionOf(pdfDoc, 31));
            assertNull(instructionOf(pdfDoc, 44));
            assertNull(instructionOf(pdfDoc, 35));
        }
    }

    @Test
    void leavesItemsWithALabelInTheirListWhateverItSays() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            PdfStructElem item = StructTree.findByObjNumber(pdfDoc.getStructTreeRoot(), 57);
            item.addKid(0, new PdfStructElem(pdfDoc, PdfName.Lbl));

            runWithFixes(pdfDoc);

            assertNull(instructionOf(pdfDoc, 57));
        }
    }

    @Test
    void marksAnItemForBreakingOutOnceWhenRunTwice() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            runWithFixes(pdfDoc);
            runWithFixes(pdfDoc);

            assertEquals(List.of("!BREAK_OUT"), segmentsOf(pdfDoc, 57));
        }
    }
}
