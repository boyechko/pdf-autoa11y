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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNameTree;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves each configured URL's {@code /O} pages to the next available position via {@link
 * PdfDocument#movePage(int, int)}. Pages of unknown URLs are skipped with a warning. Pages not
 * referenced by any configured URL are left in place; they end up at the back of the document as
 * covered pages drift forward.
 *
 * <p>Relies on the page being tracked by indirect reference (object number), so {@code movePage}
 * lookups via the original page dictionary remain valid even after earlier moves shift other pages'
 * positions. Should run <em>before</em> {@code WrapWebCapturesFix} so the structure is flat (no
 * multi-page wrappers for iText's movePage to split).
 */
public class ReorderWebCapturesFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ReorderWebCapturesFix.class);

    private static final int P_REORDER_PAGES = 7;
    private static final PdfName N_SPS = new PdfName("SPS");
    private static final PdfName N_O = new PdfName("O");

    private final List<String> orderedUrls;
    private int movedPages = 0;
    private int unknownUrls = 0;

    public ReorderWebCapturesFix(List<String> orderedUrls) {
        this.orderedUrls = List.copyOf(orderedUrls);
    }

    @Override
    public int priority() {
        return P_REORDER_PAGES;
    }

    @Override
    public String describe() {
        return "Reordered "
                + movedPages
                + " page(s) by configured URL order"
                + (unknownUrls > 0 ? " (" + unknownUrls + " URL(s) not found in /URLS)" : "");
    }

    @Override
    public void apply(DocContext ctx) {
        PdfDocument doc = ctx.doc();
        Map<String, List<PdfDictionary>> urlToPages = collectUrlPages(doc);

        int targetPos = 1;
        for (String partial : orderedUrls) {
            String matchedUrl = findMatchingUrl(partial, urlToPages.keySet());
            if (matchedUrl == null) {
                logger.warn("No /URLS entry contains: {}", partial);
                unknownUrls++;
                continue;
            }
            List<PdfDictionary> pages = urlToPages.get(matchedUrl);
            for (PdfDictionary pageDict : pages) {
                int currentPos = doc.getPageNumber(pageDict);
                if (currentPos <= 0) continue;
                if (currentPos != targetPos) {
                    doc.movePage(currentPos, targetPos);
                    movedPages++;
                }
                targetPos++;
            }
        }
    }

    /**
     * Returns the number of pages that would need to be physically moved to satisfy the configured
     * URL order. Used by the Check to decide whether to emit an issue (count == 0 means the
     * document is already in the desired order, no reorder needed).
     */
    public static int countOutOfPositionPages(PdfDocument doc, List<String> orderedUrls) {
        Map<String, List<PdfDictionary>> urlToPages = collectUrlPages(doc);
        int targetPos = 1;
        int outOfPosition = 0;
        for (String partial : orderedUrls) {
            String matchedUrl = findMatchingUrl(partial, urlToPages.keySet());
            if (matchedUrl == null) continue;
            for (PdfDictionary pageDict : urlToPages.get(matchedUrl)) {
                int currentPos = doc.getPageNumber(pageDict);
                if (currentPos <= 0) continue;
                if (currentPos != targetPos) outOfPosition++;
                targetPos++;
            }
        }
        return outOfPosition;
    }

    /**
     * Returns the <em>shortest</em> URL in {@code urls} that contains {@code partial} as a
     * substring, or null if none match. Shortest wins because the shortest URL containing a pattern
     * is the most "parent-like" — e.g. {@code www.uwb.edu/catalog} naturally targets the catalog
     * home rather than {@code www.uwb.edu/catalog/degree-programs}, even when both contain the
     * pattern. Ties (multiple URLs of equal shortest length) resolve to the first encountered.
     */
    private static String findMatchingUrl(String partial, java.util.Set<String> urls) {
        String shortest = null;
        for (String url : urls) {
            if (!url.contains(partial)) continue;
            if (shortest == null || url.length() < shortest.length()) {
                shortest = url;
            }
        }
        return shortest;
    }

    /**
     * Reads /URLS and returns URL → ordered list of page dictionaries (in {@code /O}-array order).
     * Page dictionaries are stable across {@code movePage} calls, so we capture them once here and
     * use them for position lookups during the reorder.
     */
    private static Map<String, List<PdfDictionary>> collectUrlPages(PdfDocument doc) {
        Map<String, List<PdfDictionary>> result = new LinkedHashMap<>();
        PdfNameTree urls = doc.getCatalog().getNameTree(PdfName.URLS);
        for (Map.Entry<PdfString, PdfObject> e : urls.getNames().entrySet()) {
            String url = e.getKey().toUnicodeString();
            if (!(e.getValue() instanceof PdfDictionary cs)) continue;
            if (!N_SPS.equals(cs.getAsName(PdfName.S))) continue;
            PdfArray o = cs.getAsArray(N_O);
            if (o == null || o.isEmpty()) continue;
            List<PdfDictionary> pageDicts = new ArrayList<>();
            for (int i = 0; i < o.size(); i++) {
                PdfDictionary pd = o.getAsDictionary(i);
                if (pd != null) pageDicts.add(pd);
            }
            result.put(url, pageDicts);
        }
        return result;
    }
}
