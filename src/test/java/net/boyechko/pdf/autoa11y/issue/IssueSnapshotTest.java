// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class IssueSnapshotTest extends PdfTestBase {

    @Test
    void pagelessStructElemLocationTakesPageFromElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfPage second = pdfDoc.addNewPage();
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, second);
            pdfDoc.getStructTreeRoot().addKid(p);
            DocContext ctx = new DocContext(pdfDoc);

            Locus where = Locus.atObj(StructTree.objNum(p), null, Locus.ObjKind.STRUCT_ELEM);
            Issue issue =
                    new Issue(IssueType.LANGUAGE_NOT_SET, IssueSev.WARNING, where, "m", c -> {});

            assertEquals(2, IssueSnapshot.capture(issue, ctx).pageNum());
        }
    }
}
