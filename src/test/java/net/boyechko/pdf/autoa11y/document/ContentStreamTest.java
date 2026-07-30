// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
    void treatsAnInlineFontChangeAsPartOfTheSameLine() throws Exception {
        // One visual line written out of order: a bolded word reached by a horizontal-only Td, and
        // a fragment repositioned via an absolute Tm plus a matrix-scaled Td that nets back to the
        // line's baseline (y ~= 167). None of these moves lowers the baseline, so it is one line.
        String content =
                """
                BT
                /H5 <</MCID 20 >>BDC
                12.75 0 0 12.75 47.497 167.2275 Tm
                (Mathematics Choose )Tj
                /TT0 1 Tf
                10.949 0 Td
                (one)Tj
                /TT1 1 Tf
                12.75 0 0 12.75 58.003 637.4985 Tm
                11.903 -36.884 Td
                ( course: )Tj
                EMC
                ET
                """;
        try (PdfDocument doc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            PdfPage page = doc.addNewPage();
            PdfStream stream = new PdfStream(content.getBytes(StandardCharsets.ISO_8859_1));
            stream.makeIndirect(doc);
            page.getPdfObject().put(PdfName.Contents, stream);

            SplitPlan plan = ContentStream.planLineSplit(page, 20);

            assertEquals(0, plan.splitOffsets().size(), "the fragments share one baseline");
            assertEquals(0, plan.fontChangeOffsets().size(), "so the inline bold is not a split");
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
