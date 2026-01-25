/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.ByteArrayOutputStream;
import java.util.List;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;
import org.junit.jupiter.api.Test;

class TagSingleChildFixTest {

    @Test
    void treatLblFigureAsBullet_RemovesFigureTag() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {

            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            root.addKid(li);
            PdfStructElem lbl = new PdfStructElem(pdfDoc, PdfName.Lbl);
            li.addKid(lbl);
            PdfStructElem lbody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lbody);
            PdfStructElem figure = new PdfStructElem(pdfDoc, PdfName.Figure);
            lbl.addKid(figure);

            List<PdfStructElem> beforeKids =
                    li.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            DocumentContext ctx = new DocumentContext(pdfDoc);

            IssueFix fix =
                    TagSingleChildFix.TreatLblFigureAsBullet.tryCreate(figure, lbl).orElseThrow();
            fix.apply(ctx);

            List<PdfStructElem> afterKids =
                    li.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            assertTrue(lbl.getParent() != li, "Lbl tag should be removed from LI");
            assertTrue(figure.getRole().equals(PdfName.Lbl), "Figure tag should be changed to Lbl");
            assertTrue(
                    "Bullet".equals(figure.getActualText().toUnicodeString()),
                    "Figure ActualText should be 'Bullet'");
            assertEquals(
                    beforeKids.size(),
                    afterKids.size(),
                    "LI should have same number of kids after fix");
            assertEquals(
                    beforeKids.get(1).getRole(),
                    afterKids.get(1).getRole(),
                    "LBody should remain second kid of LI");
        }
    }

    @Test
    void describe_IncludesPageNumberWhenAvailable() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            // Create a simple document structure
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem l = new PdfStructElem(pdfDoc, PdfName.L);
            root.addKid(l);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            l.addKid(div);

            DocumentContext ctx = new DocumentContext(pdfDoc);

            // Create a WrapInLI fix
            IssueFix fix = TagSingleChildFix.WrapInLI.tryCreate(div, l).orElseThrow();

            // Test both describe methods
            String basicDescription = fix.describe();
            String contextDescription = fix.describe(ctx);

            // Basic description should not have page info
            assertFalse(
                    basicDescription.contains("(p."),
                    "Basic describe should not contain page info");

            // Context description should include object number
            assertTrue(
                    contextDescription.contains("object #"),
                    "Context describe should contain object number");

            // Both should contain the core information
            assertTrue(
                    basicDescription.contains("Wrapped Div"),
                    "Should describe the wrapped element");
            assertTrue(
                    contextDescription.contains("Wrapped Div"),
                    "Should describe the wrapped element");
        }
    }
}
