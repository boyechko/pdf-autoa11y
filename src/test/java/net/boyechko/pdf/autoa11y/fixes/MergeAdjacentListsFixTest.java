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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MergeAdjacentListsFixTest extends PdfTestBase {

    private static PdfStructElem listWithItems(
            PdfDocument pdfDoc, PdfStructElem parent, int items) {
        PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
        parent.addKid(list);
        for (int i = 0; i < items; i++) {
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);
        }
        return list;
    }

    @Test
    void mergesSecondListIntoAdjacentFirst() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = listWithItems(pdfDoc, document, 1);
            PdfStructElem second = listWithItems(pdfDoc, document, 2);

            new MergeAdjacentListsFix(first, second).apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[]], LI[LBody[]], LI[LBody[]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void skipsWhenListsAreNotAdjacent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem first = listWithItems(pdfDoc, document, 1);
            PdfStructElem between = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(between);
            PdfStructElem second = listWithItems(pdfDoc, document, 1);

            new MergeAdjacentListsFix(first, second).apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[]]], P[], L[LI[LBody[]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }
}
