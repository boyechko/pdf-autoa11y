// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MergeSplitParagraphsFixTest extends PdfTestBase {

    private static PdfStructElem documentOf(PdfDocument pdfDoc) {
        return StructTree.findDocument(pdfDoc.getStructTreeRoot());
    }

    private static List<PdfStructElem> paragraphsOf(PdfDocument pdfDoc) {
        return StructTree.childrenOf(documentOf(pdfDoc), PdfStructElem.class);
    }

    @Test
    void mergesFragmentsIntoFirstParagraph() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(new Paragraph("may petition to"));
            layoutDoc.add(new Paragraph("opt out of Discovery Core"));
            layoutDoc.add(new Paragraph("course."));
            List<PdfStructElem> fragments = paragraphsOf(pdfDoc);

            new MergeSplitParagraphsFix(fragments).apply(new DocContext(pdfDoc));

            assertEquals(1, paragraphsOf(pdfDoc).size(), "Fragments should be one P");
            PdfStructElem merged = paragraphsOf(pdfDoc).get(0);
            assertTrue(StructTree.isSameElement(fragments.get(0), merged), "First P survives");
            assertEquals(
                    3, StructTree.descendantsOf(merged, PdfMcr.class).size(), "Kept all content");
        }
    }

    @Test
    void leavesParagraphsAloneWhenNoLongerAdjacent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(new Paragraph("may petition to"));
            layoutDoc.add(new Paragraph("Discovery Core"));
            layoutDoc.add(new Paragraph("opt out of Discovery Core."));
            List<PdfStructElem> paragraphs = paragraphsOf(pdfDoc);

            new MergeSplitParagraphsFix(List.of(paragraphs.get(0), paragraphs.get(2)))
                    .apply(new DocContext(pdfDoc));

            assertEquals(3, paragraphsOf(pdfDoc).size(), "Should not have merged");
        }
    }

    @Test
    void secondApplicationChangesNothing() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(new Paragraph("may petition to"));
            layoutDoc.add(new Paragraph("opt out of Discovery Core."));
            MergeSplitParagraphsFix fix = new MergeSplitParagraphsFix(paragraphsOf(pdfDoc));
            DocContext ctx = new DocContext(pdfDoc);

            fix.apply(ctx);
            fix.apply(ctx);

            assertEquals(1, paragraphsOf(pdfDoc).size(), "Fragments should be one P");
            assertEquals(
                    2,
                    StructTree.descendantsOf(paragraphsOf(pdfDoc).get(0), PdfMcr.class).size(),
                    "Content should be merged once");
        }
    }

    @Test
    void stampsMergedParagraphWithToolScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(new Paragraph("may petition to"));
            layoutDoc.add(new Paragraph("opt out of Discovery Core."));

            new MergeSplitParagraphsFix(paragraphsOf(pdfDoc)).apply(new DocContext(pdfDoc));

            PdfStructElem merged = paragraphsOf(pdfDoc).get(0);
            DocValue.Scribble scribble = StructTree.getScribble(merged);
            assertNotNull(scribble, "Merged P should carry a scribble");
            assertTrue(scribble.toolAuthored(), "Scribble should read as tool-authored");
            assertEquals(List.of("MERGE paragraphs"), scribble.segments());
        }
    }
}
