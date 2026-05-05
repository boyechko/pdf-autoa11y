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
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNameTree;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps each Web Capture URL's pages in an {@code <Article>} structure element. Walks {@code
 * /Catalog /Names /URLS}, sorts URLs by their starting page, and for each URL creates an Article
 * whose kids are the previously-direct-kids of {@code <Document>} whose first MCR falls on a page
 * in the URL's {@code /O} array. The Article's {@code /T} scribble is set to a sanitized form of
 * the URL.
 *
 * <p>Idempotent: elements already inside an Article (or that are themselves Articles) are skipped.
 * Pages outside any /URLS coverage retain their original parent. Sibling order is left for {@code
 * StructTreeOrderCheck} to reconcile after this fix runs.
 */
public class WrapWebCapturesFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(WrapWebCapturesFix.class);

    private static final int P_WRAP_CAPTURES = 8;
    private static final PdfName N_SPS = new PdfName("SPS");
    private static final PdfName N_O = new PdfName("O");

    private int wrapped = 0;
    private int skippedEmpty = 0;

    @Override
    public int priority() {
        return P_WRAP_CAPTURES;
    }

    @Override
    public String describe() {
        return "Wrapped "
                + wrapped
                + " Web Capture URL(s) in Article elements"
                + (skippedEmpty > 0
                        ? " (" + skippedEmpty + " URL(s) skipped: no kids to wrap)"
                        : "");
    }

    @Override
    public void apply(DocContext ctx) {
        PdfDocument doc = ctx.doc();
        PdfStructElem document = StructTree.findDocument(doc.getStructTreeRoot());
        if (document == null) {
            logger.debug("No /Document element under StructTreeRoot; skipping wrap");
            return;
        }

        List<UrlEntry> urlEntries = collectUrlEntries(doc);
        urlEntries.sort(Comparator.comparingInt(UrlEntry::startPage));

        Map<Integer, List<PdfStructElem>> kidsByFirstPage = indexDocumentKidsByPage(document);
        Set<PdfIndirectReference> alreadyWrapped = new HashSet<>();
        Map<String, Integer> nameCounts = new HashMap<>();

        for (UrlEntry entry : urlEntries) {
            List<PdfStructElem> toWrap = collectKidsToWrap(entry, kidsByFirstPage, alreadyWrapped);
            if (toWrap.isEmpty()) {
                skippedEmpty++;
                continue;
            }
            String scribble = uniqueName(sanitizeUrl(entry.url()), nameCounts);
            wrapInArticle(doc, document, toWrap, scribble);
            wrapped++;
        }
    }

    /**
     * Reads /URLS and returns one UrlEntry per SPS content set with at least one resolvable page.
     */
    private List<UrlEntry> collectUrlEntries(PdfDocument doc) {
        List<UrlEntry> out = new ArrayList<>();
        PdfNameTree urls = doc.getCatalog().getNameTree(PdfName.URLS);
        for (Map.Entry<PdfString, PdfObject> e : urls.getNames().entrySet()) {
            String url = e.getKey().toUnicodeString();
            if (!(e.getValue() instanceof PdfDictionary cs)) continue;
            if (!N_SPS.equals(cs.getAsName(PdfName.S))) continue;
            PdfArray o = cs.getAsArray(N_O);
            if (o == null || o.isEmpty()) continue;
            List<Integer> pages = new ArrayList<>();
            for (int i = 0; i < o.size(); i++) {
                PdfDictionary d = o.getAsDictionary(i);
                if (d == null) continue;
                int pn = doc.getPageNumber(d);
                if (pn > 0) pages.add(pn);
            }
            if (!pages.isEmpty()) {
                out.add(new UrlEntry(url, pages.get(0), pages));
            }
        }
        return out;
    }

    /** Indexes Document's direct struct-element kids by their first-MCR page. */
    private Map<Integer, List<PdfStructElem>> indexDocumentKidsByPage(PdfStructElem document) {
        Map<Integer, List<PdfStructElem>> out = new HashMap<>();
        List<IStructureNode> kids = document.getKids();
        if (kids == null) return out;
        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfStructElem elem)) continue;
            int firstPage = firstLeafPage(elem);
            if (firstPage > 0) {
                out.computeIfAbsent(firstPage, k -> new ArrayList<>()).add(elem);
            }
        }
        return out;
    }

    /** Returns the page number of the first MCR found via a depth-first walk, or 0 if none. */
    private int firstLeafPage(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return 0;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfMcr mcr) {
                int page = StructTree.pageOf(mcr);
                if (page > 0) return page;
            } else if (kid instanceof PdfStructElem child) {
                int page = firstLeafPage(child);
                if (page > 0) return page;
            }
        }
        return 0;
    }

    /**
     * Returns the kids that should be moved under this URL's Article: those whose first-page is in
     * the URL's /O list, that haven't already been claimed by an earlier URL, and that aren't
     * themselves Article elements (idempotency).
     */
    private List<PdfStructElem> collectKidsToWrap(
            UrlEntry entry,
            Map<Integer, List<PdfStructElem>> kidsByFirstPage,
            Set<PdfIndirectReference> alreadyWrapped) {
        List<PdfStructElem> out = new ArrayList<>();
        for (int page : entry.pages()) {
            List<PdfStructElem> kids = kidsByFirstPage.get(page);
            if (kids == null) continue;
            for (PdfStructElem kid : kids) {
                if (PdfName.Art.equals(kid.getRole())) continue;
                PdfIndirectReference ref = kid.getPdfObject().getIndirectReference();
                if (ref != null && !alreadyWrapped.add(ref)) continue;
                out.add(kid);
            }
        }
        return out;
    }

    /**
     * Creates an Article under {@code document}, sets its scribble, and reparents each kid into it.
     * Article must be added to its parent before kids can be added (iText {@code addKid} contract).
     */
    private void wrapInArticle(
            PdfDocument doc, PdfStructElem document, List<PdfStructElem> toWrap, String scribble) {
        PdfStructElem article = new PdfStructElem(doc, PdfName.Art);
        document.addKid(article);
        StructTree.setScribble(article, scribble);
        for (PdfStructElem kid : toWrap) {
            document.removeKid(kid);
            article.addKid(kid);
        }
    }

    /**
     * Returns {@code base} on first occurrence, {@code base-2}/{@code base-3}/... on later ones.
     */
    private String uniqueName(String base, Map<String, Integer> counts) {
        int n = counts.getOrDefault(base, 0);
        counts.put(base, n + 1);
        return n == 0 ? base : base + "-" + (n + 1);
    }

    /**
     * Sanitizes a URL into a scribble-safe identifier: strip protocol and host, take last path
     * segment, drop {@code .html(.htm)} extension, replace remaining non-ident chars with {@code
     * -}.
     */
    static String sanitizeUrl(String url) {
        String s = url.replaceFirst("^https?://", "");
        int firstSlash = s.indexOf('/');
        s = firstSlash < 0 ? "" : s.substring(firstSlash + 1);
        s = s.replaceFirst("/$", "");
        int lastSlash = s.lastIndexOf('/');
        if (lastSlash >= 0) s = s.substring(lastSlash + 1);
        s = s.replaceFirst("(?i)\\.html?$", "");
        s = s.replaceAll("[^A-Za-z0-9_-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "index" : s;
    }

    private record UrlEntry(String url, int startPage, List<Integer> pages) {}
}
