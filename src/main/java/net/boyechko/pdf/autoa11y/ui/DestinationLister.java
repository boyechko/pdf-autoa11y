/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.ui;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNameTree;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocValue;

/** Renders a tabular listing of named destinations from a PDF's /Catalog /Names /Dests tree. */
public final class DestinationLister {

    private static final int PAGE_COL_WIDTH = 6;
    private static final int NAME_COL_WIDTH = 60;
    private static final int PAGE_NUM_DIGITS = 3;

    private static final String ROW_FORMAT =
            String.format("%%-%ds  %%-%ds%%n", PAGE_COL_WIDTH, NAME_COL_WIDTH);
    private static final String PAGE_NUM_FORMAT = "%0" + PAGE_NUM_DIGITS + "d";

    private DestinationLister() {}

    /**
     * Renders the named destinations of a PDF as a sorted table, one row per destination, ordered
     * by target page number (then by name). Destinations whose target page cannot be resolved sort
     * first under page {@code 000}.
     */
    public static String dumpToString(PdfDocument pdfDoc) {
        PdfNameTree destsTree = pdfDoc.getCatalog().getNameTree(PdfName.Dests);
        Map<PdfString, PdfObject> entries = destsTree.getNames();

        List<Row> rows = new ArrayList<>(entries.size());
        for (Map.Entry<PdfString, PdfObject> e : entries.entrySet()) {
            int pageNum = pageNumberOf(e.getValue());
            // Replace non-printable ASCII characters with Unicode replacement character (0xFFFD)
            String name = e.getKey().toUnicodeString().replaceAll("[\\x00-\\x1F]", "�");
            rows.add(new Row(pageNum, name));
        }
        rows.sort(Comparator.comparingInt(Row::pageNum).thenComparing(Row::name));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(ROW_FORMAT, "Page", "Name"));
        sb.append(
                String.format(ROW_FORMAT, "-".repeat(PAGE_COL_WIDTH), "-".repeat(NAME_COL_WIDTH)));
        for (Row row : rows) {
            sb.append(
                    String.format(
                            ROW_FORMAT, String.format(PAGE_NUM_FORMAT, row.pageNum()), row.name()));
        }
        return sb.toString();
    }

    private static int pageNumberOf(PdfObject dest) {
        DocValue.Destination resolved = DocValue.resolveDestination(dest);
        return resolved instanceof DocValue.Destination.GoToPage gp ? gp.pageNum() : 0;
    }

    private record Row(int pageNum, String name) {}
}
