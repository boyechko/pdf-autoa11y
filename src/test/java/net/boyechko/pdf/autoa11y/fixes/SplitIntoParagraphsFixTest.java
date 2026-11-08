// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class SplitIntoParagraphsFixTest extends PdfTestBase {

    /**
     * Catalog pages whose P #99 holds three two-line paragraphs in one MCR, starting "Students are
     * required", "A recipient", and "A student with".
     */
    private static final Path FUSED_PDF = Path.of("src/test/resources/catalog_250-252.pdf");

    private static final int FUSED_P = 99;

    /**
     * P #84 fuses a two-line paragraph ending "this catalog." with one whose last line holds Link
     * #85 ("Academic Calendar") between two MCRs.
     */
    private static final int LINKED_P = 84;

    private static final int LINK = 85;

    @Test
    void splitsFusedParagraphIntoSiblingPs() throws Exception {
        try (PdfDocument doc = openForStamping(FUSED_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = StructTree.findByObjNumber(doc.getStructTreeRoot(), FUSED_P);
            PdfStructElem parent = (PdfStructElem) p.getParent();
            int index = StructTree.findKidIndex(parent, p);

            new SplitIntoParagraphsFix(p, List.of(2, 2, 2)).apply(ctx);

            PdfStructElem first = (PdfStructElem) parent.getKids().get(index);
            PdfStructElem second = (PdfStructElem) parent.getKids().get(index + 1);
            PdfStructElem third = (PdfStructElem) parent.getKids().get(index + 2);
            assertEquals(PdfName.P, second.getRole());
            assertEquals(PdfName.P, third.getRole());
            assertTrue(
                    Content.getTextForElement(first, ctx).strip().endsWith("their withdrawal."),
                    "first P ends the first paragraph");
            assertTrue(
                    Content.getTextForElement(second, ctx).strip().startsWith("A recipient"),
                    "second P starts the second paragraph");
            assertTrue(
                    Content.getTextForElement(third, ctx).strip().startsWith("A student with"),
                    "third P starts the third paragraph");
        }
    }

    @Test
    void movesInlineLinkWholeIntoTheParagraphHoldingIt() throws Exception {
        try (PdfDocument doc = openForStamping(FUSED_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = StructTree.findByObjNumber(doc.getStructTreeRoot(), LINKED_P);
            PdfStructElem parent = (PdfStructElem) p.getParent();
            int index = StructTree.findKidIndex(parent, p);

            new SplitIntoParagraphsFix(p, List.of(2, 2)).apply(ctx);

            PdfStructElem first = (PdfStructElem) parent.getKids().get(index);
            PdfStructElem second = (PdfStructElem) parent.getKids().get(index + 1);
            String secondText = Content.getTextForElement(second, ctx).strip();
            assertTrue(
                    Content.getTextForElement(first, ctx).strip().endsWith("this catalog."),
                    "first P ends the first paragraph");
            assertTrue(secondText.startsWith("During summer"), "second P starts the second");
            assertTrue(secondText.endsWith("specific dates."), "second P keeps the line's tail");
            PdfStructElem link = StructTree.findByObjNumber(doc.getStructTreeRoot(), LINK);
            assertTrue(
                    StructTree.isSameElement((PdfStructElem) link.getParent(), second),
                    "the Link moves whole into the second P");
        }
    }

    @Test
    void refusesSizesThatDisagreeWithTheLineCount() throws Exception {
        try (PdfDocument doc = openForStamping(FUSED_PDF)) {
            DocContext ctx = new DocContext(doc);
            PdfStructElem p = StructTree.findByObjNumber(doc.getStructTreeRoot(), FUSED_P);
            PdfStructElem parent = (PdfStructElem) p.getParent();
            int kidCount = parent.getKids().size();

            assertThrows(
                    IllegalStateException.class,
                    () -> new SplitIntoParagraphsFix(p, List.of(2, 2, 1)).apply(ctx));
            assertEquals(kidCount, parent.getKids().size(), "the tree is left untouched");
        }
    }

    private PdfDocument openForStamping(Path input) throws Exception {
        return new PdfDocument(new PdfReader(input.toString()), new PdfWriter(testOutputStream()));
    }
}
