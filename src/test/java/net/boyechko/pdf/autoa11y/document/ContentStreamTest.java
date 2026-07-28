// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import org.junit.jupiter.api.Test;

class ContentStreamTest {

    /**
     * A catalog cover page whose page-1 headings lump differently-sized lines into one
     * marked-content block: MCID 15 ("Graduate Programs" at 19.5, "Computing and Software Systems"
     * at 21.75) and MCID 16 (a two-line title wrapped at 15, then "Program Overview" at 12.75).
     */
    private static final Path CATALOG_067 = Path.of("src/test/resources/catalog_067.pdf");

    @Test
    void reportsFontChangeBetweenDifferentlySizedLines() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(CATALOG_067.toString()))) {
            PdfPage page = doc.getPage(1);
            SplitPlan plan = ContentStream.planLineSplit(page, 15);

            assertEquals(1, plan.splitOffsets().size(), "two lines yield one line boundary");
            assertEquals(
                    plan.splitOffsets(),
                    plan.fontChangeOffsets(),
                    "the two lines differ in size, so the sole boundary is a font change");
        }
    }

    @Test
    void ignoresSameSizeWrapWhenReportingFontChanges() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(CATALOG_067.toString()))) {
            PdfPage page = doc.getPage(1);
            SplitPlan plan = ContentStream.planLineSplit(page, 16);

            assertEquals(2, plan.splitOffsets().size(), "three lines yield two line boundaries");
            assertEquals(
                    1,
                    plan.fontChangeOffsets().size(),
                    "only the last line changes size; the same-size wrap is not a font change");
            assertEquals(
                    plan.splitOffsets().get(1),
                    plan.fontChangeOffsets().get(0),
                    "the font change is the second boundary, not the wrap");
        }
    }
}
