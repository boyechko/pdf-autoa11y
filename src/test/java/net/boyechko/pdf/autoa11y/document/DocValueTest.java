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
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.Test;

class DocValueTest extends PdfTestBase {

    @Test
    void resolveDestinationReturnsNullForNullInput() {
        assertNull(DocValue.resolveDestination(null));
    }

    @Test
    void resolveDestinationConvertsPdfNameToGoToNamed() {
        DocValue.Destination result = DocValue.resolveDestination(new PdfName("MyChapter"));
        assertEquals(new DocValue.Destination.GoToNamed("MyChapter"), result);
    }

    @Test
    void resolveDestinationConvertsPdfStringToGoToNamed() {
        DocValue.Destination result = DocValue.resolveDestination(new PdfString("MyChapter"));
        assertEquals(new DocValue.Destination.GoToNamed("MyChapter"), result);
    }

    @Test
    void resolveDestinationReturnsNullForEmptyArray() {
        assertNull(DocValue.resolveDestination(new PdfArray()));
    }

    @Test
    void resolveDestinationConvertsPageArrayToGoToPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfArray dest = new PdfArray();
            dest.add(page2.getPdfObject());
            dest.add(PdfName.Fit);

            assertEquals(new DocValue.Destination.GoToPage(2), DocValue.resolveDestination(dest));
        }
    }

    @Test
    void resolveDestinationUnwrapsWrapperDictByFollowingDEntry() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();

            PdfArray destArray = new PdfArray();
            destArray.add(page.getPdfObject());
            destArray.add(PdfName.Fit);

            PdfDictionary wrapper = new PdfDictionary();
            wrapper.put(PdfName.D, destArray);

            assertEquals(
                    new DocValue.Destination.GoToPage(1), DocValue.resolveDestination(wrapper));
        }
    }

    @Test
    void resolveDestinationFollowsIndirectReferences() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();

            PdfArray destArray = new PdfArray();
            destArray.add(page.getPdfObject());
            destArray.add(PdfName.Fit);
            destArray.makeIndirect(pdfDoc);

            assertEquals(
                    new DocValue.Destination.GoToPage(1),
                    DocValue.resolveDestination(destArray.getIndirectReference()));
        }
    }

    @Test
    void resolveDestinationReturnsNullWhenArrayFirstElementIsNotDict() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();

            PdfArray dest = new PdfArray();
            dest.add(new PdfNumber(1));
            dest.add(PdfName.Fit);

            assertNull(DocValue.resolveDestination(dest));
        }
    }

    @Test
    void resolveDestinationReturnsNullWhenDictIsNotARegisteredPage() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();

            // A dictionary that's part of the document but not a registered page
            PdfDictionary notAPage = new PdfDictionary();
            notAPage.put(PdfName.Type, PdfName.Annot);
            notAPage.makeIndirect(pdfDoc);

            PdfArray dest = new PdfArray();
            dest.add(notAPage);
            dest.add(PdfName.Fit);

            assertNull(DocValue.resolveDestination(dest));
        }
    }

    @Test
    void toolAuthoredScribbleStripsMarkFromBody() {
        DocValue.Scribble scribble =
                new DocValue.Scribble(StructTree.SCRIBBLE_TOOL_MARK + "!SET_ROLE H1");

        assertTrue(scribble.toolAuthored());
        assertEquals("!SET_ROLE H1", scribble.body());
    }

    @Test
    void userAuthoredScribbleHasNoToolMark() {
        DocValue.Scribble scribble = new DocValue.Scribble("!SET_ROLE H1");

        assertFalse(scribble.toolAuthored());
        assertEquals("!SET_ROLE H1", scribble.body());
    }

    @Test
    void segmentsSplitBodyAndExcludeToolMark() {
        DocValue.Scribble scribble =
                new DocValue.Scribble(
                        StructTree.SCRIBBLE_TOOL_MARK
                                + "?H3 (expected H2)"
                                + StructTree.SCRIBBLE_SEPARATOR
                                + "23.75pt");

        assertTrue(scribble.toolAuthored());
        assertEquals(List.of("?H3 (expected H2)", "23.75pt"), scribble.segments());
    }
}
