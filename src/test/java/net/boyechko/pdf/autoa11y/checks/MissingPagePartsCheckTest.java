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
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.NormalizePageParts;
import org.junit.jupiter.api.Test;

class MissingPagePartsCheckTest extends PdfTestBase {

    @Test
    void detectsUnbucketedDirectDocumentChildren() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            assertTrue(
                    MissingPagePartsCheck.needsNormalization(new DocContext(pdfDoc)),
                    "Should detect missing page-level Part grouping");
        }
    }

    @Test
    void noIssueWhenDirectChildrenAlreadyUnderParts() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem part1 = new PdfStructElem(pdfDoc, PdfName.Part, page1);
            PdfStructElem part2 = new PdfStructElem(pdfDoc, PdfName.Part, page2);
            document.addKid(part1);
            document.addKid(part2);
            part1.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            part2.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            assertFalse(
                    MissingPagePartsCheck.needsNormalization(new DocContext(pdfDoc)),
                    "Should not report issue for already normalized structure");
        }
    }

    @Test
    void detectsNeedWhenDocumentIsMissingOnMultiPageFile() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            root.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            root.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            assertTrue(
                    MissingPagePartsCheck.needsNormalization(new DocContext(pdfDoc)),
                    "Should detect need even without Document element");
        }
    }

    /** After applying the NormalizePageParts fix, detection should report no more issues. */
    @Test
    void visitorReportsNoIssuesAfterFixApplied() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page1 = pdfDoc.addNewPage();
            PdfPage page2 = pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();
            pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page1));
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P, page2));

            DocContext ctx = new DocContext(pdfDoc);
            assertTrue(
                    MissingPagePartsCheck.needsNormalization(ctx),
                    "Precondition: should need normalization");

            NormalizePageParts fix = new NormalizePageParts();
            fix.apply(ctx);

            DocContext freshCtx = new DocContext(pdfDoc);
            assertFalse(
                    MissingPagePartsCheck.needsNormalization(freshCtx),
                    "After fix, should not need normalization");
        }
    }
}
