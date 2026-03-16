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

            boolean changed = RoleMap.clear(pdfDoc);

            assertTrue(changed);
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
