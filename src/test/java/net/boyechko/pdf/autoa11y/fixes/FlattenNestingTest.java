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

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class FlattenNestingTest extends PdfTestBase {

    @Test
    void flattensDivWhenParentStartsWithSingleKEntry() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem part = new PdfStructElem(pdfDoc, PdfName.Part);
            document.addKid(part);

            PdfStructElem sectToFlatten = new PdfStructElem(pdfDoc, PdfName.Sect);
            part.addKid(sectToFlatten);
            PdfStructElem siblingSect = new PdfStructElem(pdfDoc, PdfName.Sect);
            part.addKid(siblingSect);

            PdfStructElem divToFlatten = new PdfStructElem(pdfDoc, PdfName.Div);
            sectToFlatten.addKid(divToFlatten);
            PdfStructElem paragraph = new PdfStructElem(pdfDoc, PdfName.P);
            divToFlatten.addKid(paragraph);

            assertNull(
                    sectToFlatten.getPdfObject().getAsArray(PdfName.K),
                    "Sect should start with a single-object /K entry");

            new FlattenNesting(List.of(sectToFlatten, divToFlatten)).apply(new DocContext(pdfDoc));

            List<IStructureNode> partKids = part.getKids();
            assertTrue(
                    containsPdfObject(partKids, paragraph.getPdfObject()),
                    "Paragraph child should be promoted under Part");
            assertFalse(
                    containsPdfObject(partKids, sectToFlatten.getPdfObject()),
                    "Sect wrapper should be removed");
            assertFalse(
                    containsPdfObject(partKids, divToFlatten.getPdfObject()),
                    "Div wrapper should be removed");
        }
    }

    private static boolean containsPdfObject(List<IStructureNode> nodes, PdfDictionary target) {
        if (nodes == null) {
            return false;
        }
        for (IStructureNode node : nodes) {
            if (node instanceof PdfStructElem elem) {
                if (elem.getPdfObject() == target) {
                    return true;
                }
                if (containsPdfObject(elem.getKids(), target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
