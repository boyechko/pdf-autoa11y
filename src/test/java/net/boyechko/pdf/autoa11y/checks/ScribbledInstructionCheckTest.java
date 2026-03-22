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
package net.boyechko.pdf.autoa11y.checks;

import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class ScribbledInstructionCheckTest extends PdfTestBase {

    @Test
    void detectsInstructionScribble() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    p.getPdfObject()
                            .put(
                                    PdfName.T,
                                    new PdfString(SCRIBBLE_PREFIX + "!ADD_CHILD Reference[Lbl[]]"));
                    document.addKid(p);
                });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testOutputPath().toString()))) {
            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix(), "Scribbled instruction issues should have a fix");
        }
    }

    @Test
    void ignoresNonInstructionScribbles() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "artifact"));
                    document.addKid(p);
                });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testOutputPath().toString()))) {
            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void ignoresElementsWithoutScribble() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    document.addKid(p);
                });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testOutputPath().toString()))) {
            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void detectsMultipleInstructionScribbles() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p1 = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    p1.getPdfObject()
                            .put(
                                    PdfName.T,
                                    new PdfString(SCRIBBLE_PREFIX + "!ADD_CHILD Reference[Lbl[]]"));
                    document.addKid(p1);

                    PdfStructElem p2 = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    p2.getPdfObject()
                            .put(
                                    PdfName.T,
                                    new PdfString(SCRIBBLE_PREFIX + "!ADD_CHILD Reference[Lbl[]]"));
                    document.addKid(p2);
                });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testOutputPath().toString()))) {
            IssueList issues = runCheck(pdfDoc);

            assertEquals(2, issues.size());
        }
    }

    @Test
    void detectsAddParentScribble() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
                    p.getPdfObject()
                            .put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "!ADD_PARENT Note[]"));
                    document.addKid(p);
                });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testOutputPath().toString()))) {
            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.SCRIBBLED_INSTRUCTION, issues.get(0).type());
            assertNotNull(issues.get(0).fix());
        }
    }

    @Test
    void ignoresRegularTitle() throws Exception {
        createStructuredTestPdf(
                (pdfDoc, firstPage, root, document) -> {
                    PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
                    h1.getPdfObject().put(PdfName.T, new PdfString("Chapter 1"));
                    document.addKid(h1);
                });

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(testOutputPath().toString()))) {
            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    private IssueList runCheck(PdfDocument pdfDoc) {
        StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
        walker.addVisitor(new ScribbledInstructionCheck());
        return walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }
}
