// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.Issue;
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
            assertNull(issues.get(0).fix(), "A header row with no data below is not fixable");
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

    @Test
    void headerRowAboveDataRowsIsScopedAsColumn() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem table = addTable(pdfDoc);
            PdfStructElem header = addRow(pdfDoc, table, PdfName.TH, PdfName.TH);
            addRow(pdfDoc, table, PdfName.TD, PdfName.TD);
            addRow(pdfDoc, table, PdfName.TD, PdfName.TD);

            IssueList issues = runCheck(pdfDoc);
            assertEquals(2, issues.size());
            DocContext ctx = new DocContext(pdfDoc);
            for (Issue issue : issues) {
                assertNotNull(issue.fix());
                issue.fix().apply(ctx);
            }

            for (Tag th : Tag.of(header).children()) {
                assertEquals("Column", DocValue.Scope.of(th.elem()).value());
            }
            assertTrue(runCheck(pdfDoc).isEmpty());
        }
    }

    @Test
    void headerRowInsideTHeadIsScopedAsColumn() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem table = addTable(pdfDoc);
            PdfStructElem thead = new PdfStructElem(pdfDoc, PdfName.THead);
            table.addKid(thead);
            PdfStructElem tbody = new PdfStructElem(pdfDoc, PdfName.TBody);
            table.addKid(tbody);
            addRow(pdfDoc, thead, PdfName.TH, PdfName.TH);
            addRow(pdfDoc, tbody, PdfName.TD, PdfName.TD);

            IssueList issues = runCheck(pdfDoc);
            assertEquals(2, issues.size());
            assertTrue(issues.stream().allMatch(issue -> issue.fix() != null));
        }
    }

    @Test
    void headerColumnBelowHeaderRowIsScopedAsRow() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem table = addTable(pdfDoc);
            PdfStructElem header = addRow(pdfDoc, table, PdfName.TH, PdfName.TH);
            PdfStructElem second = addRow(pdfDoc, table, PdfName.TH, PdfName.TD);
            PdfStructElem third = addRow(pdfDoc, table, PdfName.TH, PdfName.TD);

            IssueList issues = runCheck(pdfDoc);
            assertEquals(4, issues.size());
            DocContext ctx = new DocContext(pdfDoc);
            for (Issue issue : issues) {
                assertNotNull(issue.fix());
                issue.fix().apply(ctx);
            }

            List<Tag> headerCells = Tag.of(header).children();
            assertEquals(
                    "Column", DocValue.Scope.of(headerCells.get(0).elem()).value(), "corner cell");
            assertEquals("Column", DocValue.Scope.of(headerCells.get(1).elem()).value());
            for (PdfStructElem row : List.of(second, third)) {
                PdfStructElem rowHeader = Tag.of(row).children().get(0).elem();
                assertEquals("Row", DocValue.Scope.of(rowHeader).value());
            }
            assertTrue(runCheck(pdfDoc).isEmpty());
        }
    }

    @Test
    void headerColumnWithGapsLeavesTableUnfixed() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem table = addTable(pdfDoc);
            addRow(pdfDoc, table, PdfName.TH, PdfName.TH);
            addRow(pdfDoc, table, PdfName.TH, PdfName.TD);
            addRow(pdfDoc, table, PdfName.TD, PdfName.TD);

            IssueList issues = runCheck(pdfDoc);
            assertEquals(3, issues.size());
            assertTrue(issues.stream().allMatch(issue -> issue.fix() == null));
        }
    }

    @Test
    void headerOutsideFirstColumnLeavesTableUnfixed() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem table = addTable(pdfDoc);
            addRow(pdfDoc, table, PdfName.TH, PdfName.TH);
            addRow(pdfDoc, table, PdfName.TD, PdfName.TH);

            IssueList issues = runCheck(pdfDoc);
            assertEquals(3, issues.size());
            assertTrue(issues.stream().allMatch(issue -> issue.fix() == null));
        }
    }

    @Test
    void mixedFirstRowLeavesTableUnfixed() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructElem table = addTable(pdfDoc);
            addRow(pdfDoc, table, PdfName.TH, PdfName.TD);
            addRow(pdfDoc, table, PdfName.TD, PdfName.TD);

            IssueList issues = runCheck(pdfDoc);
            assertEquals(1, issues.size());
            assertNull(issues.get(0).fix());
        }
    }

    private static PdfStructElem addTableRow(PdfDocument pdfDoc) {
        return addRow(pdfDoc, addTable(pdfDoc));
    }

    private static PdfStructElem addTable(PdfDocument pdfDoc) {
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        pdfDoc.getStructTreeRoot().addKid(document);
        PdfStructElem table = new PdfStructElem(pdfDoc, PdfName.Table);
        document.addKid(table);
        return table;
    }

    private static PdfStructElem addRow(
            PdfDocument pdfDoc, PdfStructElem parent, PdfName... cellRoles) {
        PdfStructElem row = new PdfStructElem(pdfDoc, PdfName.TR);
        parent.addKid(row);
        for (PdfName role : cellRoles) {
            row.addKid(new PdfStructElem(pdfDoc, role));
        }
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
