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
package net.boyechko.pdf.autoa11y.visitors;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

public class FigureWithTextVisitorTest extends PdfTestBase {
    private Path createTestPdf() throws Exception {
        String filename = "FigureWithTextVisitorTest.pdf";
        OutputStream outputStream = testOutputStream(filename);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem figure = new PdfStructElem(pdfDoc, PdfName.Figure, page);
            document.addKid(figure);

            PdfMcrNumber mcr = new PdfMcrNumber(page, figure);
            figure.addKid(mcr);

            PdfDictionary props = new PdfDictionary();
            props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));
            PdfCanvas canvas = new PdfCanvas(page);
            canvas.beginMarkedContent(PdfName.Figure, props);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            canvas.beginText();
            canvas.setFontAndSize(font, 24);
            canvas.moveText(100, 750);
            canvas.showText("This is a Figure");
            canvas.endText();
            canvas.rectangle(75, 700, 200, 100);
            canvas.stroke();
            canvas.endMarkedContent();
        }
        Path outputPath = testOutputPath(filename);
        return outputPath;
    }

    @Test
    void figureWithTextVisitorCreatesIssuesForFigureElements() throws Exception {
        Path pdfFile = createTestPdf();
        assertTrue(Files.exists(pdfFile), "PDF file should exist");
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new FigureWithTextVisitor());
            IssueList issues = walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
            assertTrue(issues.size() > 0, "Should create at least 1 issue for Figure");
            assertTrue(
                    issues.stream().anyMatch(i -> i.type() == IssueType.FIGURE_WITH_TEXT),
                    "Should create an issue for Figure with text");
        }
    }
}
