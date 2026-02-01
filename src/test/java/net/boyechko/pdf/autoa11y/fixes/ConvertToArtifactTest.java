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
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import org.junit.jupiter.api.Test;

class ConvertToArtifactTest extends PdfTestBase {

    @Test
    void removesElementAndAssociatedLinkAnnotation() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(100, 100, 200, 20))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem linkElem = new PdfStructElem(pdfDoc, PdfName.Link, page);
            document.addKid(linkElem);

            int structParentIndex = pdfDoc.getNextStructParentIndex();
            PdfObjRef objRef = new PdfObjRef(linkAnnot, linkElem, structParentIndex);
            linkElem.addKid(objRef);

            assertEquals(
                    1, document.getKids().size(), "Document should have Link child before fix");
            assertEquals(
                    1, page.getAnnotations().size(), "Page should have Link annotation before fix");

            DocumentContext ctx = new DocumentContext(pdfDoc);
            new ConvertToArtifact(linkElem).apply(ctx);

            assertTrue(
                    document.getKids() == null || document.getKids().isEmpty(),
                    "Link should be removed from Document");
            assertTrue(
                    page.getAnnotations().isEmpty(), "Link annotation should be removed from page");
        }
    }

    @Test
    void removesElementFromStructTreeRoot() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            root.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            new ConvertToArtifact(p).apply(ctx);

            assertTrue(
                    root.getKids() == null || root.getKids().isEmpty(),
                    "Root should have no kids after removal");
        }
    }

    @Test
    void doesNothingWhenNoParent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            DocumentContext ctx = new DocumentContext(pdfDoc);

            ConvertToArtifact fix = new ConvertToArtifact(p);
            assertDoesNotThrow(() -> fix.apply(ctx));
        }
    }

    @Test
    void invalidatesDescendantFix() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem parent = new PdfStructElem(pdfDoc, new PdfName("Sect"));
            PdfStructElem child = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(parent);
            parent.addKid(child);

            ConvertToArtifact parentFix = new ConvertToArtifact(parent);
            ConvertToArtifact childFix = new ConvertToArtifact(child);

            assertTrue(
                    parentFix.invalidates(childFix),
                    "Ancestor fix should invalidate descendant fix");
            assertFalse(
                    childFix.invalidates(parentFix),
                    "Descendant fix should not invalidate ancestor fix");
        }
    }
}
