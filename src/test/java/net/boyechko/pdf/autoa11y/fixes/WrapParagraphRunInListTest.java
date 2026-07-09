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
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class WrapParagraphRunInListTest extends PdfTestBase {

    @Test
    void convertsSuspectedParagraphRunToList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(pdfDoc, new PdfName("P"));
            PdfStructElem p2 = new PdfStructElem(pdfDoc, new PdfName("P"));
            PdfStructElem p3 = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(p1);
            document.addKid(p2);
            document.addKid(p3);
            assertEquals("Document[P[], P[], P[]]", StructTree.toRoleTree(document).toString());

            DocContext ctx = new DocContext(pdfDoc);
            WrapParagraphRunInList fix = new WrapParagraphRunInList(document, List.of(p1, p2, p3));
            fix.apply(ctx);

            assertEquals(
                    "Document[L[LI[LBody[P[]]], LI[LBody[P[]]], LI[LBody[P[]]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void nestsSublistIntoPrecedingListItem() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            document.addKid(list);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(li);
            PdfStructElem lBody = new PdfStructElem(pdfDoc, PdfName.LBody);
            li.addKid(lBody);
            PdfStructElem itemP = new PdfStructElem(pdfDoc, new PdfName("P"));
            lBody.addKid(itemP);

            PdfStructElem sub1 = new PdfStructElem(pdfDoc, new PdfName("P"));
            PdfStructElem sub2 = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(sub1);
            document.addKid(sub2);

            DocContext ctx = new DocContext(pdfDoc);
            WrapParagraphRunInList fix =
                    new WrapParagraphRunInList(document, List.of(sub1, sub2), list);
            fix.apply(ctx);

            assertEquals(
                    "Document[L[LI[LBody[P[], L[LI[LBody[P[]]], LI[LBody[P[]]]]]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void fallsBackToSiblingListWhenNestTargetHasNoLBody() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem list = new PdfStructElem(pdfDoc, PdfName.L);
            document.addKid(list);

            PdfStructElem sub1 = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(sub1);

            DocContext ctx = new DocContext(pdfDoc);
            WrapParagraphRunInList fix = new WrapParagraphRunInList(document, List.of(sub1), list);
            fix.apply(ctx);

            assertEquals(
                    "Document[L[], L[LI[LBody[P[]]]]]", StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void doesNothingWithRegularParagraphs() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(pdfDoc, new PdfName("P"));
            PdfStructElem p2 = new PdfStructElem(pdfDoc, new PdfName("P"));
            PdfStructElem p3 = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(p1);
            document.addKid(p2);
            document.addKid(p3);
            assertEquals("Document[P[], P[], P[]]", StructTree.toRoleTree(document).toString());

            DocContext ctx = new DocContext(pdfDoc);
            WrapParagraphRunInList fix = new WrapParagraphRunInList(document, List.of(p1, p2, p3));
            fix.apply(ctx);

            assertEquals(
                    "Document[L[LI[LBody[P[]]], LI[LBody[P[]]], LI[LBody[P[]]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }
}
