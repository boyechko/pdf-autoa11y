// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class ReorderWebCapturesFixTest extends PdfTestBase {

    @Test
    void reordersPagesPerConfiguredUrlOrder() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();

            // Build a 3-page tagged PDF with flat structure (one P per page) so movePage can
            // reorder cleanly without splitting any multi-page wrapper.
            PdfPage pageA = pdfDoc.addNewPage();
            PdfPage pageB = pdfDoc.addNewPage();
            PdfPage pageC = pdfDoc.addNewPage();
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            pdfDoc.getStructTreeRoot().addKid(document);
            for (PdfPage p : List.of(pageA, pageB, pageC)) {
                PdfStructElem para = new PdfStructElem(pdfDoc, PdfName.P, p);
                document.addKid(para);
                para.addKid(new PdfMcrNumber(p, para));
            }

            // Build /Catalog /Names /URLS with two SPS content sets:
            //   urlA -> /O = [pageA]
            //   urlC -> /O = [pageC]
            // pageB is intentionally NOT covered by any URL (a "drift to back" candidate).
            registerUrl(pdfDoc, "https://example.com/a", pageA);
            registerUrl(pdfDoc, "https://example.com/c", pageC);

            // Configured order uses substring patterns (no protocol prefix). The fix matches each
            // partial against /URLS keys via String.contains. After the fix:
            //   pos 1 = pageC, pos 2 = pageA, pos 3 = pageB (unconfigured, drifts to back).
            new ReorderWebCapturesFix(List.of("example.com/c", "example.com/a"))
                    .apply(new DocContext(pdfDoc));

            assertEquals(pageC.getPdfObject(), pdfDoc.getPage(1).getPdfObject());
            assertEquals(pageA.getPdfObject(), pdfDoc.getPage(2).getPdfObject());
            assertEquals(pageB.getPdfObject(), pdfDoc.getPage(3).getPdfObject());
        }
    }

    @Test
    void ambiguousPatternResolvesToShortestUrl() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage pageHome = pdfDoc.addNewPage();
            PdfPage pageSub = pdfDoc.addNewPage();
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            pdfDoc.getStructTreeRoot().addKid(document);
            for (PdfPage p : List.of(pageHome, pageSub)) {
                PdfStructElem para = new PdfStructElem(pdfDoc, PdfName.P, p);
                document.addKid(para);
                para.addKid(new PdfMcrNumber(p, para));
            }
            // Register sub-page FIRST so first-appearance would mistakenly bind to it. The fix's
            // shortest-match strategy must still resolve "example.com/catalog" to the home URL.
            registerUrl(pdfDoc, "https://example.com/catalog/degree-programs", pageSub);
            registerUrl(pdfDoc, "https://example.com/catalog/", pageHome);

            new ReorderWebCapturesFix(List.of("example.com/catalog")).apply(new DocContext(pdfDoc));

            // Expected: home (shortest URL match) ends up at position 1, sub at 2 (drifted).
            assertEquals(pageHome.getPdfObject(), pdfDoc.getPage(1).getPdfObject());
            assertEquals(pageSub.getPdfObject(), pdfDoc.getPage(2).getPdfObject());
        }
    }

    @Test
    void countOutOfPositionPagesReturnsZeroWhenAlreadyOrdered() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage pageA = pdfDoc.addNewPage();
            PdfPage pageB = pdfDoc.addNewPage();
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            pdfDoc.getStructTreeRoot().addKid(document);
            for (PdfPage p : List.of(pageA, pageB)) {
                PdfStructElem para = new PdfStructElem(pdfDoc, PdfName.P, p);
                document.addKid(para);
                para.addKid(new PdfMcrNumber(p, para));
            }
            registerUrl(pdfDoc, "https://example.com/a", pageA);
            registerUrl(pdfDoc, "https://example.com/b", pageB);

            List<String> order = List.of("example.com/a", "example.com/b");

            // First run: both pages are already where the configured order wants them.
            // (pageA -> p1 because urlA is first; pageB -> p2 because urlB is second.)
            assertEquals(0, ReorderWebCapturesFix.countOutOfPositionPages(pdfDoc, order));

            // Reverse the order; now pageB should be at p1 and pageA at p2 — both pages out of pos.
            List<String> reversed = List.of("example.com/b", "example.com/a");
            assertEquals(2, ReorderWebCapturesFix.countOutOfPositionPages(pdfDoc, reversed));

            // Apply once: pages get reordered.
            ReorderWebCapturesFix fix = new ReorderWebCapturesFix(reversed);
            fix.apply(new DocContext(pdfDoc));

            // Now the reversed order is satisfied; running countOutOfPositionPages again returns 0.
            assertEquals(0, ReorderWebCapturesFix.countOutOfPositionPages(pdfDoc, reversed));
        }
    }

    @Test
    void unknownConfiguredUrlIsSkipped() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage pageA = pdfDoc.addNewPage();
            PdfPage pageB = pdfDoc.addNewPage();
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            pdfDoc.getStructTreeRoot().addKid(document);
            for (PdfPage p : List.of(pageA, pageB)) {
                PdfStructElem para = new PdfStructElem(pdfDoc, PdfName.P, p);
                document.addKid(para);
                para.addKid(new PdfMcrNumber(p, para));
            }
            // Only urlB is registered; the configured "missing" URL won't match.
            registerUrl(pdfDoc, "https://example.com/b", pageB);

            new ReorderWebCapturesFix(
                            List.of("https://example.com/missing", "https://example.com/b"))
                    .apply(new DocContext(pdfDoc));

            // urlB's pageB lands at position 1 (the missing URL slot is skipped, not occupied).
            assertEquals(pageB.getPdfObject(), pdfDoc.getPage(1).getPdfObject());
            assertEquals(pageA.getPdfObject(), pdfDoc.getPage(2).getPdfObject());
        }
    }

    /**
     * Adds an SPS content set under {@code /Catalog /Names /URLS} mapping {@code url} to a single
     * page via the {@code /O} array.
     */
    private static void registerUrl(PdfDocument doc, String url, PdfPage page) {
        PdfDictionary contentSet = new PdfDictionary();
        contentSet.put(PdfName.S, new PdfName("SPS"));
        PdfArray o = new PdfArray();
        o.add(page.getPdfObject());
        contentSet.put(new PdfName("O"), o);
        // makeIndirect so the name tree can hold it as an indirect reference, matching real PDFs.
        contentSet.makeIndirect(doc);
        doc.getCatalog().getNameTree(PdfName.URLS).addEntry(new PdfString(url), contentSet);
    }
}
