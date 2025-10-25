package net.boyechko.pdf.autoa11y.fixes;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;

import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

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

            List<PdfStructElem> beforeKids = li.getKids().stream()
                    .filter(k -> k instanceof PdfStructElem)
                    .map(k -> (PdfStructElem) k)
                    .toList();

            DocumentContext ctx = new DocumentContext(pdfDoc);

            IssueFix fix = TagSingleChildFix.TreatLblFigureAsBullet.tryCreate(figure, lbl).orElseThrow();
            fix.apply(ctx);

            List<PdfStructElem> afterKids = li.getKids().stream()
                    .filter(k -> k instanceof PdfStructElem)
                    .map(k -> (PdfStructElem) k)
                    .toList();

            assertTrue(lbl.getParent() != li, "Lbl tag should be removed from LI");
            assertTrue(figure.getRole().equals(PdfName.Lbl), "Figure tag should be changed to Lbl");
            assertTrue("Bullet".equals(figure.getActualText().toUnicodeString()), "Figure ActualText should be 'Bullet'");
            assertEquals(beforeKids.size(), afterKids.size(), "LI should have same number of kids after fix");
            assertEquals(beforeKids.get(1).getRole(), afterKids.get(1).getRole(), "LBody should remain second kid of LI");
        }
    }
}