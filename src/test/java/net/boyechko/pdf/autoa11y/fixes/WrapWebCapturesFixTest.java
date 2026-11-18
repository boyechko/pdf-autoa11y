// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Tag;
import org.junit.jupiter.api.Test;

class WrapWebCapturesFixTest extends PdfTestBase {

    @Test
    void wrapsKidsOnCapturedPagesInAnArticle() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            doc.getStructTreeRoot().addKid(document);
            List<Tag> paragraphs = paragraphPerPage(doc, document, 3);
            captureUrl(doc, "https://example.com/a.html", 1, 2);

            new WrapWebCapturesFix().apply(new DocContext(doc));

            Tag article = articlesUnder(document).getFirst();
            assertEquals(List.of(paragraphs.get(0), paragraphs.get(1)), article.children());
            assertEquals(Tag.of(document), paragraphs.get(2).parent());
            assertEquals(List.of("example-com-a"), article.scribble().segments());
        }
    }

    @Test
    void wrappingTwiceLeavesOneArticle() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            doc.getStructTreeRoot().addKid(document);
            List<Tag> paragraphs = paragraphPerPage(doc, document, 2);
            captureUrl(doc, "https://example.com/a.html", 1, 2);

            new WrapWebCapturesFix().apply(new DocContext(doc));
            new WrapWebCapturesFix().apply(new DocContext(doc));

            List<Tag> articles = articlesUnder(document);
            assertEquals(1, articles.size());
            assertEquals(paragraphs, articles.getFirst().children());
        }
    }

    /** Adds a page per paragraph, each P carrying one MCR on its own page. */
    private static List<Tag> paragraphPerPage(PdfDocument doc, PdfStructElem document, int count) {
        List<Tag> paragraphs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PdfPage page = doc.addNewPage();
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            document.addKid(p);
            Tag.of(p).addMarkedContent(page);
            paragraphs.add(Tag.of(p));
        }
        return paragraphs;
    }

    /** Records a Web Capture of the URL as an SPS content set over the given pages. */
    private static void captureUrl(PdfDocument doc, String url, int... pageNums) {
        List<PdfObject> pages = new ArrayList<>();
        for (int pageNum : pageNums) {
            pages.add(doc.getPage(pageNum).getPdfObject());
        }
        PdfDictionary contentSet = new PdfDictionary();
        contentSet.put(PdfName.S, new PdfName("SPS"));
        contentSet.put(PdfName.O, new PdfArray(pages));
        doc.getCatalog().getNameTree(PdfName.URLS).addEntry(new PdfString(url), contentSet);
    }

    private static List<Tag> articlesUnder(PdfStructElem document) {
        return Tag.of(document).children().stream().filter(t -> t.role().equals("Art")).toList();
    }
}
