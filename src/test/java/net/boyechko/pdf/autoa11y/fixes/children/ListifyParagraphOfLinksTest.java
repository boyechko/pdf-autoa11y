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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

public class ListifyParagraphOfLinksTest extends PdfTestBase {
    @Test
    void convertsParagraphOfLinksToList() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, structTreeRoot, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
                    document.addKid(p);
                    for (int i = 0; i < 5; i++) {
                        PdfStructElem link = new PdfStructElem(pdfDoc, PdfName.Link);
                        p.addKid(link);
                    }
                    assertEquals(
                            "Document[P[Link, Link, Link, Link, Link]]",
                            StructTree.toRoleTree(document).toString(),
                            "Document should have P child with 5 Link children before fix");

                    DocContext ctx = new DocContext(pdfDoc);
                    List<PdfStructElem> kids =
                            p.getKids().stream()
                                    .map(kid -> (PdfStructElem) kid)
                                    .collect(Collectors.toList());
                    new ListifyParagraphOfLinks(p, kids).apply(ctx);

                    assertEquals(
                            "Document[L[LI[LBody[Link]], LI[LBody[Link]], LI[LBody[Link]], LI[LBody[Link]], LI[LBody[Link]]]]",
                            StructTree.toRoleTree(document).toString(),
                            "Document should have P child converted to L with 5 LI children after fix");
                });
    }
}
