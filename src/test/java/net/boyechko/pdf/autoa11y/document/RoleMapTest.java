// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.util.Map;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.Test;

class RoleMapTest extends PdfTestBase {
    @Test
    void clearRemovesRoleMapKeyFromStructTreeRoot() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            pdfDoc.getStructTreeRoot()
                    .getPdfObject()
                    .put(PdfName.RoleMap, mapping("CustomHeading", "H1"));

            int cleared = RoleMap.clear(pdfDoc);

            assertEquals(1, cleared);
            assertFalse(pdfDoc.getStructTreeRoot().getPdfObject().containsKey(PdfName.RoleMap));
        }
    }

    @Test
    void replaceWritesSuppliedRoleMappings() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            boolean changed =
                    RoleMap.replace(
                            pdfDoc, Map.of("CustomHeading", "H2", "/CustomFigure", "/Figure"));

            assertTrue(changed);
            PdfDictionary roleMap =
                    pdfDoc.getStructTreeRoot().getPdfObject().getAsDictionary(PdfName.RoleMap);
            assertNotNull(roleMap);
            assertEquals(PdfName.H2, roleMap.getAsName(new PdfName("CustomHeading")));
            assertEquals(PdfName.Figure, roleMap.getAsName(new PdfName("CustomFigure")));
        }
    }

    @Test
    void hasRoleMapReturnsTrueWhenPresent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            pdfDoc.getStructTreeRoot()
                    .getPdfObject()
                    .put(PdfName.RoleMap, mapping("CustomHeading", "H1"));

            assertTrue(RoleMap.hasRoleMap(pdfDoc));
        }
    }

    @Test
    void hasRoleMapReturnsFalseAfterClear() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            RoleMap.clear(pdfDoc);

            assertFalse(RoleMap.hasRoleMap(pdfDoc));
        }
    }

    @Test
    void replaceWithEmptyMapRemovesRoleMap() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            pdfDoc.getStructTreeRoot()
                    .getPdfObject()
                    .put(PdfName.RoleMap, mapping("CustomHeading", "H1"));

            RoleMap.replace(pdfDoc, Map.of());

            assertFalse(pdfDoc.getStructTreeRoot().getPdfObject().containsKey(PdfName.RoleMap));
        }
    }

    private PdfDictionary mapping(String fromRole, String toRole) {
        PdfDictionary roleMap = new PdfDictionary();
        roleMap.put(new PdfName(fromRole), new PdfName(toRole));
        return roleMap;
    }
}
