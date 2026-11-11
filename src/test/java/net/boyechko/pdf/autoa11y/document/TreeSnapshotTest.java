// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeSnapshotTest {
    @Test
    void retainsOrderedChildrenAndFullTextAfterThePdfIsClosed() {
        TreeNode<TreeSnapshot.Value> snapshot;
        String fullText = "A full paragraph that must remain available for searching. ".repeat(4);
        try (PdfDocument doc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            doc.setTagged();
            var page = doc.addNewPage();
            var article = new PdfStructElem(doc, PdfName.Art);
            doc.getStructTreeRoot().addKid(article);
            article.put(PdfName.T, new PdfString("__REVIEW"));
            article.put(PdfName.Pg, page.getPdfObject());
            article.addKid(new PdfMcrNumber(new PdfNumber(0), article));
            article.addKid(new PdfStructElem(doc, PdfName.H3));
            article.addKid(new PdfMcrNumber(new PdfNumber(1), article));
            var contents =
                    Map.of(
                            new Content.PageMcid(1, 0),
                            new Content.McidContent(
                                    fullText, List.of(), Set.of(Content.ContentKind.TEXT)));

            snapshot = TreeSnapshot.capture(article, contents);
            article.put(PdfName.T, new PdfString("__CHANGED"));
        }

        var article = assertInstanceOf(TreeSnapshot.Element.class, snapshot.value());
        assertEquals("Art", article.rawRole());
        assertEquals("REVIEW", article.scribble().value());
        assertEquals(1, article.page());
        assertTrue(article.objNum() > 0);
        assertEquals(3, snapshot.children().size());
        var first =
                assertInstanceOf(
                        TreeSnapshot.MarkedContent.class, snapshot.children().get(0).value());
        assertEquals(fullText, first.text());
        assertEquals(0, first.mcid());
        assertEquals(Set.of(Content.ContentKind.TEXT), first.kinds());
        assertInstanceOf(TreeSnapshot.Element.class, snapshot.children().get(1).value());
        var last =
                assertInstanceOf(
                        TreeSnapshot.MarkedContent.class, snapshot.children().get(2).value());
        assertEquals(1, last.mcid());
    }
}
