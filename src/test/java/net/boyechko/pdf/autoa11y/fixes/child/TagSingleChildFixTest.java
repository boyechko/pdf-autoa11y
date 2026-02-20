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
package net.boyechko.pdf.autoa11y.fixes.child;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.junit.jupiter.api.Test;

class TagSingleChildFixTest extends PdfTestBase {

    @Test
    void treatLblFigureAsBullet() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {

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

            IssueFix fix = TreatLblFigureAsBullet.tryCreate(figure, lbl);
            assertNotNull(fix, "Fix should be created for Lbl[Figure] pattern");
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
    void wrapLDivInLI() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem l = new PdfStructElem(pdfDoc, PdfName.L);
            root.addKid(l);
            PdfStructElem div = new PdfStructElem(pdfDoc, PdfName.Div);
            l.addKid(div);

            DocumentContext ctx = new DocumentContext(pdfDoc);

            IssueFix fix = WrapInLI.tryCreate(div, l);
            assertNotNull(fix, "Fix should be created for L[Div] pattern");
            fix.apply(ctx);

            List<PdfStructElem> lKids =
                    l.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            assertEquals(1, lKids.size(), "L should have one child after wrapping");
            PdfStructElem li = lKids.get(0);
            assertEquals(PdfName.LI, li.getRole(), "L child should be LI");

            List<PdfStructElem> liKids =
                    li.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            assertEquals(1, liKids.size(), "LI should have one child");
            PdfStructElem lbody = liKids.get(0);
            assertEquals(PdfName.LBody, lbody.getRole(), "LI child should be LBody");

            List<PdfStructElem> lbodyKids =
                    lbody.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            assertEquals(1, lbodyKids.size(), "LBody should have one child");
            assertEquals(PdfName.Div, lbodyKids.get(0).getRole(), "LBody child should remain Div");
            assertEquals(
                    div.getPdfObject(),
                    lbodyKids.get(0).getPdfObject(),
                    "Original Div should be under LBody");
        }
    }

    @Test
    void wrapLIParagraphInLBody() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            root.addKid(li);
            PdfStructElem paragraph = new PdfStructElem(pdfDoc, PdfName.P);
            li.addKid(paragraph);

            DocumentContext ctx = new DocumentContext(pdfDoc);

            IssueFix fix = WrapInLBody.tryCreate(paragraph, li);
            assertNotNull(fix, "Fix should be created for LI[P] pattern");
            fix.apply(ctx);

            List<PdfStructElem> liKids =
                    li.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            assertEquals(1, liKids.size(), "LI should have one child after wrapping");
            PdfStructElem lbody = liKids.get(0);
            assertEquals(PdfName.LBody, lbody.getRole(), "LI child should be LBody");

            List<PdfStructElem> lbodyKids =
                    lbody.getKids().stream()
                            .filter(k -> k instanceof PdfStructElem)
                            .map(k -> (PdfStructElem) k)
                            .toList();

            assertEquals(1, lbodyKids.size(), "LBody should have one child");
            assertEquals(PdfName.P, lbodyKids.get(0).getRole(), "LBody child should remain P");
            assertEquals(
                    paragraph.getPdfObject(),
                    lbodyKids.get(0).getPdfObject(),
                    "Original paragraph should be under LBody");
        }
    }

    @Test
    void extractLBodyToList_firesForLBodyUnderP() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            p.addKid(lBody);

            IssueFix fix = ExtractLBodyToList.tryCreate(lBody, p);
            assertNotNull(fix, "Fix should be created for P[LBody] pattern");
            assertInstanceOf(ExtractLBodyToList.class, fix);
        }
    }

    @Test
    void extractLBodyToList_skipsLBodyUnderLI() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            root.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);

            IssueFix fix = ExtractLBodyToList.tryCreate(lBody, li);
            assertNull(fix, "Fix should NOT be created for LI[LBody] — already valid");
        }
    }

    @Test
    void extractLBodyToList_skipsLBodyUnderL() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem l = new PdfStructElem(pdfDoc, PdfName.L);
            root.addKid(l);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            l.addKid(lBody);

            IssueFix fix = ExtractLBodyToList.tryCreate(lBody, l);
            assertNull(fix, "Fix should NOT be created for L[LBody] — WrapInLI handles this");
        }
    }

    @Test
    void extractLBodyToList_dispatchedByCreateIfApplicable() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            p.addKid(lBody);

            IssueFix fix = TagSingleChildFix.createIfApplicable(lBody, p);
            assertNotNull(fix, "createIfApplicable should find a fix for P[LBody]");
            assertInstanceOf(
                    ExtractLBodyToList.class,
                    fix,
                    "createIfApplicable should dispatch to ExtractLBodyToList, not WrapInLI");
        }
    }
}
