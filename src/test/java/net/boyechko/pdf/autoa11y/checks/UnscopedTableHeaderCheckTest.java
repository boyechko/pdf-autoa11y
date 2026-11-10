// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import org.junit.jupiter.api.Test;

class UnscopedTableHeaderCheckTest extends PdfTestBase {

    @Test
    void flagsHeaderCellWithoutScope() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem row = addTableRow(pdfDoc);
            row.addKid(new PdfStructElem(pdfDoc, PdfName.TH));

            IssueList issues = runCheck(pdfDoc);
            assertEquals(1, issues.size());
            assertEquals(IssueType.TABLE_HEADER_UNSCOPED, issues.get(0).type());
            assertNull(issues.get(0).fix(), "Scope direction needs human judgment");
        }
    }

    @Test
    void acceptsHeaderCellWithTableScope() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem row = addTableRow(pdfDoc);
            PdfStructElem th = new PdfStructElem(pdfDoc, PdfName.TH);
            row.addKid(th);
            th.getPdfObject().put(PdfName.A, scopeAttrs(PdfName.Table, PdfName.Column));

            assertTrue(runCheck(pdfDoc).isEmpty());
        }
    }

    @Test
    void flagsScopeOwnedByOtherThanTable() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem row = addTableRow(pdfDoc);
            PdfStructElem th = new PdfStructElem(pdfDoc, PdfName.TH);
            row.addKid(th);
            th.getPdfObject().put(PdfName.A, scopeAttrs(PdfName.Layout, PdfName.Row));

            assertEquals(1, runCheck(pdfDoc).size());
        }
    }

    @Test
    void flagsScopeWithUndefinedValue() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem row = addTableRow(pdfDoc);
            PdfStructElem th = new PdfStructElem(pdfDoc, PdfName.TH);
            row.addKid(th);
            th.getPdfObject().put(PdfName.A, scopeAttrs(PdfName.Table, new PdfName("Diagonal")));

            assertEquals(1, runCheck(pdfDoc).size());
        }
    }

    @Test
    void ignoresDataCells() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem row = addTableRow(pdfDoc);
            row.addKid(new PdfStructElem(pdfDoc, PdfName.TD));

            assertTrue(runCheck(pdfDoc).isEmpty());
        }
    }

    private static PdfStructElem addTableRow(PdfDocument pdfDoc) {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        root.addKid(document);
        PdfStructElem table = new PdfStructElem(pdfDoc, PdfName.Table);
        document.addKid(table);
        PdfStructElem row = new PdfStructElem(pdfDoc, PdfName.TR);
        table.addKid(row);
        return row;
    }

    private static PdfDictionary scopeAttrs(PdfName owner, PdfName scope) {
        PdfDictionary attrs = new PdfDictionary();
        attrs.put(PdfName.O, owner);
        attrs.put(PdfName.Scope, scope);
        return attrs;
    }

    private static IssueList runCheck(PdfDocument pdfDoc) {
        StructTreeWalker walker = new StructTreeWalker();
        walker.addVisitor(new UnscopedTableHeaderCheck());
        return walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }
}
