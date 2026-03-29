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

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.checks.MisartifactedTextCheck;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.junit.jupiter.api.Test;

class MisartifactedTextFixTest extends PdfTestBase {

    @Test
    void insertsScribbledLblSignpost() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    document.addKid(p);
                    PdfMcrNumber mcr = new PdfMcrNumber(firstPage, p);
                    p.addKid(mcr);

                    PdfDictionary props = new PdfDictionary();
                    props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));

                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    PdfCanvas canvas = new PdfCanvas(firstPage);

                    canvas.beginMarkedContent(new PdfName("Artifact"));
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 750)
                            .showText("42")
                            .endText();
                    canvas.endMarkedContent();

                    canvas.beginMarkedContent(PdfName.P, props);
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 700)
                            .showText("Body text")
                            .endText();
                    canvas.endMarkedContent();
                });

        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(testOutputPath().toString()),
                        new PdfWriter(
                                testOutputPath("insertsReferenceLblSignpost_fixed.pdf")
                                        .toString()))) {
            DocContext ctx = new DocContext(pdfDoc);

            IssueList issues = new MisartifactedTextCheck().findIssues(ctx);
            assertEquals(1, issues.size());
            issues.get(0).fix().apply(ctx);

            // Verify structure tree has a scribbled Lbl signpost
            PdfStructElem docElem = StructTree.findDocument(pdfDoc.getStructTreeRoot());
            var kids = docElem.getKids();
            boolean hasScribbledLbl =
                    kids.stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .anyMatch(MisartifactedTextFixTest::isScribbledLbl);
            assertTrue(hasScribbledLbl, "Should have a scribbled Lbl signpost");
        }
    }

    @Test
    void insertsSignpostBeforeNeighbor() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
                    document.addKid(h1);
                    PdfMcrNumber mcr1 = new PdfMcrNumber(firstPage, h1);
                    h1.addKid(mcr1);

                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    document.addKid(p);
                    PdfMcrNumber mcr2 = new PdfMcrNumber(firstPage, p);
                    p.addKid(mcr2);

                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    PdfCanvas canvas = new PdfCanvas(firstPage);

                    PdfDictionary props1 = new PdfDictionary();
                    props1.put(PdfName.MCID, new PdfNumber(mcr1.getMcid()));
                    canvas.beginMarkedContent(PdfName.H1, props1);
                    canvas.beginText()
                            .setFontAndSize(font, 14)
                            .moveText(100, 750)
                            .showText("Heading")
                            .endText();
                    canvas.endMarkedContent();

                    // Artifact digit between H1 and P
                    canvas.beginMarkedContent(new PdfName("Artifact"));
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 730)
                            .showText("3")
                            .endText();
                    canvas.endMarkedContent();

                    PdfDictionary props2 = new PdfDictionary();
                    props2.put(PdfName.MCID, new PdfNumber(mcr2.getMcid()));
                    canvas.beginMarkedContent(PdfName.P, props2);
                    canvas.beginText()
                            .setFontAndSize(font, 10)
                            .moveText(100, 700)
                            .showText("Body text")
                            .endText();
                    canvas.endMarkedContent();
                });

        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(testOutputPath().toString()),
                        new PdfWriter(
                                testOutputPath("insertsSignpostBeforeNeighbor_fixed.pdf")
                                        .toString()))) {
            DocContext ctx = new DocContext(pdfDoc);
            IssueList issues = new MisartifactedTextCheck().findIssues(ctx);
            assertEquals(1, issues.size());
            issues.get(0).fix().apply(ctx);

            // Scribbled Lbl should appear as a sibling near the neighbor
            PdfStructElem docElem = StructTree.findDocument(pdfDoc.getStructTreeRoot());
            var kids = docElem.getKids();
            boolean hasScribbledLbl =
                    kids.stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .anyMatch(MisartifactedTextFixTest::isScribbledLbl);
            assertTrue(hasScribbledLbl, "Should have a scribbled Lbl signpost");
        }
    }

    private static boolean isScribbledLbl(PdfStructElem elem) {
        if (!"Lbl".equals(elem.getRole().getValue())) return false;
        var scribble = StructTree.getScribble(elem);
        return scribble != null && scribble.value().startsWith("misartifacted ");
    }
}
