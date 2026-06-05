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
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MistaggedHeadingFixTest extends PdfTestBase {

    @Test
    void marksElementWithToolAuthoredSetRoleInstruction() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            new MistaggedHeadingFix(p, PdfName.H3).apply(new DocContext(pdfDoc));

            // The fix proposes, rather than applies: the role stays P and a tool-authored
            // instruction is scribbled for a later ScribbledInstruction pass to execute.
            assertEquals(PdfName.P, p.getRole());
            DocValue.Scribble scribble = StructTree.getScribble(p);
            assertNotNull(scribble);
            assertTrue(scribble.toolAuthored(), "Marked instruction should be tool-authored");
            assertEquals("!SET_ROLE H3", scribble.body());
        }
    }
}
