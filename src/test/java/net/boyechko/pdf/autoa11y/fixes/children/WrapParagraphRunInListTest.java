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
package net.boyechko.pdf.autoa11y.fixes.children;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class WrapParagraphRunInListTest extends PdfTestBase {

    @Test
    void convertsSuspectedParagraphRunToList() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, structTreeRoot, document) -> {
                    PdfStructElem p1 = new PdfStructElem(pdfDoc, new PdfName("P"));
                    PdfStructElem p2 = new PdfStructElem(pdfDoc, new PdfName("P"));
                    PdfStructElem p3 = new PdfStructElem(pdfDoc, new PdfName("P"));
                    document.addKid(p1);
                    document.addKid(p2);
                    document.addKid(p3);
                    assertEquals("Document[P, P, P]", StructTree.toRoleTree(document).toString());

                    DocContext ctx = new DocContext(pdfDoc);
                    WrapParagraphRunInList fix =
                            new WrapParagraphRunInList(document, List.of(p1, p2, p3));
                    fix.apply(ctx);

                    assertEquals(
                            "Document[L[LI[LBody[P]], LI[LBody[P]], LI[LBody[P]]]]",
                            StructTree.toRoleTree(document).toString());
                });
    }

    @Test
    void doesNothingWithRegularParagraphs() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, structTreeRoot, document) -> {
                    PdfStructElem p1 = new PdfStructElem(pdfDoc, new PdfName("P"));
                    PdfStructElem p2 = new PdfStructElem(pdfDoc, new PdfName("P"));
                    PdfStructElem p3 = new PdfStructElem(pdfDoc, new PdfName("P"));
                    document.addKid(p1);
                    document.addKid(p2);
                    document.addKid(p3);
                    assertEquals("Document[P, P, P]", StructTree.toRoleTree(document).toString());

                    DocContext ctx = new DocContext(pdfDoc);
                    WrapParagraphRunInList fix =
                            new WrapParagraphRunInList(document, List.of(p1, p2, p3));
                    fix.apply(ctx);

                    assertEquals(
                            "Document[L[LI[LBody[P]], LI[LBody[P]], LI[LBody[P]]]]",
                            StructTree.toRoleTree(document).toString());
                });
    }
}
