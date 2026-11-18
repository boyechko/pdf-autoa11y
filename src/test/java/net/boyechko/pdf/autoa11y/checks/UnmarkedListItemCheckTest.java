// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later

package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.Tag;
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

            assertTrue(segmentsOf(pdfDoc, 30).contains("ITEMS 2, MARKERS 2"));
            assertTrue(segmentsOf(pdfDoc, 34).contains("ITEMS 3, MARKERS 3"));
            assertTrue(segmentsOf(pdfDoc, 47).contains("ITEMS 1, MARKERS 1"));
        }
    }

    @Test
    void countsNoMarkersForItemsThatAreReallyHeadings() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 52).contains("ITEMS 2, MARKERS 0"));
        }
    }

    @Test
    void countsBulletCharactersSetInItemLabels() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(LABELLED_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 42).contains("ITEMS 3, MARKERS 3"));
        }
    }

    @Test
    void countsNumbersSetInItemLabelsAsMarkers() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NUMBERED_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertTrue(segmentsOf(pdfDoc, 49).contains("ITEMS 3, MARKERS 3"));
        }
    }

    @Test
    void replacesTheItemCountAnEarlierFixLeftAndKeepsItsHistory() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertEquals(List.of("REBUILD outline", "ITEMS 2, MARKERS 2"), segmentsOf(pdfDoc, 30));
        }
    }

    @Test
    void stampsTheCountsOnceWhenRunTwice() throws Exception {
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(NESTED_PDF.toString()), new PdfWriter(testOutputStream()))) {
            walkWith(pdfDoc, new UnmarkedListItemCheck());
            walkWith(pdfDoc, new UnmarkedListItemCheck());

            assertEquals(List.of("ITEMS 3, MARKERS 3"), segmentsOf(pdfDoc, 34));
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
}
