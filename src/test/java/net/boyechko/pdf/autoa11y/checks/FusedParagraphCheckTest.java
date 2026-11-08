// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class FusedParagraphCheckTest extends PdfTestBase {

    /**
     * Catalog pages whose P tags include clean paragraphs (#87, #101), single-MCR Ps fusing two to
     * four paragraphs (#93, #97, #99), a P whose two paragraphs surround a Link kid (#84), and
     * fragments a merge must first join (#68, #70).
     */
    private static final Path FUSED_PDF = Path.of("src/test/resources/catalog_250-252.pdf");

    private static final String FULL_LINE = "word word word word word word word word";

    private IssueList checkBlock(String... lines) throws Exception {
        return checkBlockIn(List.of(), lines);
    }

    /**
     * Draws the lines as one marked-content block owned by a single P, nested in the given wrapper
     * roles under Document, and runs the check on the saved file, whose content streams text
     * extraction can read.
     */
    private IssueList checkBlockIn(List<PdfName> wrappers, String... lines) throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            pdfDoc.getStructTreeRoot().addKid(document);
            PdfStructElem parent = document;
            for (PdfName role : wrappers) {
                PdfStructElem wrapper = new PdfStructElem(pdfDoc, role);
                parent.addKid(wrapper);
                parent = wrapper;
            }
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            parent.addKid(p);
            PdfMcr mcr = StructTree.addNewMcr(p, page);

            PdfDictionary props = new PdfDictionary();
            props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));
            PdfCanvas canvas = new PdfCanvas(page);
            canvas.beginMarkedContent(PdfName.P, props)
                    .beginText()
                    .setFontAndSize(PdfFontFactory.createFont(StandardFonts.HELVETICA), 12)
                    .moveText(72, 700);
            for (String line : lines) {
                canvas.showText(line).moveText(0, -14);
            }
            canvas.endText().endMarkedContent();
        }
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            return new FusedParagraphCheck().findIssues(new DocContext(pdfDoc));
        }
    }

    @Test
    void detectsParagraphsFusedIntoCatalogPs() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(FUSED_PDF.toString()))) {
            IssueList issues = new FusedParagraphCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(
                    List.of(84, 93, 97, 99), issues.stream().map(i -> i.where().objNum()).toList());
            assertTrue(issues.stream().allMatch(i -> i.type() == IssueType.PARAGRAPH_FUSED));
        }
    }

    @Test
    void detectsSentenceEndingShortLineFollowedByFullOne() throws Exception {
        IssueList issues = checkBlock(FULL_LINE, FULL_LINE, "end.", FULL_LINE, "end.");

        assertEquals(1, issues.size());
    }

    @Test
    void leavesLumpedListItemsToTheListCheck() throws Exception {
        IssueList issues =
                checkBlockIn(
                        List.of(PdfName.L, PdfName.LI, PdfName.LBody),
                        FULL_LINE,
                        FULL_LINE,
                        "end.",
                        FULL_LINE,
                        "end.");

        assertTrue(issues.isEmpty());
    }

    @Test
    void ignoresShortLinesThatDoNotEndSentences() throws Exception {
        IssueList issues =
                checkBlock(
                        "Jane Doe", "Office of Graduate Studies, Box 358500", "Bothell, WA 98011");

        assertTrue(issues.isEmpty());
    }

    @Test
    void ignoresBlockWithoutWrappedLines() throws Exception {
        IssueList issues = checkBlock("Yes.", "No.", "Maybe so, perhaps.");

        assertTrue(issues.isEmpty());
    }

    @Test
    void splitParagraphsAreNotFlaggedAgain() throws Exception {
        Path fixed = testOutputPath("fixed.pdf");
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(FUSED_PDF.toString()), new PdfWriter(fixed.toString()))) {
            DocContext ctx = new DocContext(pdfDoc);
            new FusedParagraphCheck().findIssues(ctx).applyFixes(ctx);
        }

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(fixed.toString()))) {
            assertTrue(new FusedParagraphCheck().findIssues(new DocContext(pdfDoc)).isEmpty());
        }
    }
}
