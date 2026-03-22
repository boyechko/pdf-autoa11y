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
import org.junit.jupiter.api.Test;

class ScribbledInstructionFixTest extends PdfTestBase {

    @Test
    void addParentCreatesWrapper() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_PARENT Note[]").apply(ctx);

            PdfStructElem newParent = (PdfStructElem) p.getParent();
            assertEquals("Note", newParent.getRole().getValue());
            assertEquals("Document", ((PdfStructElem) newParent.getParent()).getRole().getValue());
        }
    }

    @Test
    void addParentPreservesPosition() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem document = new PdfStructElem(pdfDoc, new PdfName("Document"));
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1);
            document.addKid(h1);
            PdfStructElem target = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(target);
            PdfStructElem trailing = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(trailing);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(target, "!ADD_PARENT Note[]").apply(ctx);

            var kids = document.getKids();
            assertEquals(3, kids.size());
            assertEquals("H1", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("Note", ((PdfStructElem) kids.get(1)).getRole().getValue());
            assertEquals("P", ((PdfStructElem) kids.get(2)).getRole().getValue());
        }
    }

    @Test
    void addChildCreatesNestedStructure() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_CHILD Reference[Lbl[]]").apply(ctx);

            var kids = p.getKids();
            assertFalse(kids.isEmpty());
            PdfStructElem reference = (PdfStructElem) kids.get(0);
            assertEquals("Reference", reference.getRole().getValue());

            var refKids = reference.getKids();
            assertFalse(refKids.isEmpty());
            assertEquals("Lbl", ((PdfStructElem) refKids.get(0)).getRole().getValue());
        }
    }

    @Test
    void addChildCreatesEmptyElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(p, "!ADD_CHILD Span[]").apply(ctx);

            var kids = p.getKids();
            assertEquals(1, kids.size());
            assertEquals("Span", ((PdfStructElem) kids.get(0)).getRole().getValue());
        }
    }

    @Test
    void addChildCreatesSiblingElements() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
            root.addKid(li);

            DocContext ctx = new DocContext(pdfDoc);
            new ScribbledInstructionFix(li, "!ADD_CHILD Lbl[],LBody[]").apply(ctx);

            var kids = li.getKids();
            assertEquals(2, kids.size());
            assertEquals("Lbl", ((PdfStructElem) kids.get(0)).getRole().getValue());
            assertEquals("LBody", ((PdfStructElem) kids.get(1)).getRole().getValue());
        }
    }

    @Test
    void unsupportedInstructionThrows() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ScribbledInstructionFix(p, "!UNKNOWN_OP Foo[]").apply(ctx));
        }
    }
}
