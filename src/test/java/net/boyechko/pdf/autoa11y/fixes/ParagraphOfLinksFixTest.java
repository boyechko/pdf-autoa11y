// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

public class ParagraphOfLinksFixTest extends PdfTestBase {
    @Test
    void convertsParagraphOfLinksToList() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);
            for (int i = 0; i < 5; i++) {
                PdfStructElem link = new PdfStructElem(pdfDoc, PdfName.Link);
                p.addKid(link);
            }
            assertEquals(
                    "Document[P[Link[], Link[], Link[], Link[], Link[]]]",
                    StructTree.toRoleTree(document).toString(),
                    "Document should have P child with 5 Link children before fix");

            DocContext ctx = new DocContext(pdfDoc);
            List<PdfStructElem> kids =
                    p.getKids().stream()
                            .map(kid -> (PdfStructElem) kid)
                            .collect(Collectors.toList());
            new ParagraphOfLinksFix(p, kids).apply(ctx);

            assertEquals(
                    "Document[L[LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]], LI[LBody[Link[]]]]]",
                    StructTree.toRoleTree(document).toString(),
                    "Document should have P child converted to L with 5 LI children after fix");
        }
    }
}
