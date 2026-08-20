// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_PREFIX;
import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_TOOL_MARK;
import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.checks.StaleScribbleCheck.Scope;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class StaleScribbleCheckTest extends PdfTestBase {

    @Test
    void detectsAnnotatedTitle() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "NeedsReview"));
            document.addKid(h1);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size());
            assertEquals(IssueType.STALE_SCRIBBLE, issues.get(0).type());
            assertNotNull(issues.get(0).fix(), "Stale scribbles should have an automatic fix");
        }
    }

    @Test
    void ignoresRegularTitle() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject().put(PdfName.T, new PdfString("Chapter 1"));
            document.addKid(h1);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void ignoresElementsWithoutTitle() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void detectsMultipleStaleScribbles() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "FixHeadingLevel"));
            document.addKid(h1);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "ShouldBeList"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(2, issues.size());
            assertTrue(issues.stream().allMatch(i -> i.type() == IssueType.STALE_SCRIBBLE));
        }
    }

    @Test
    void toolAuthoredScopeIgnoresUserScribbles() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "NeedsReview"));
            document.addKid(h1);

            IssueList issues = runCheck(pdfDoc, Scope.TOOL_AUTHORED);

            assertEquals(0, issues.size());
        }
    }

    @Test
    void toolAuthoredScopeDetectsToolScribbles() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject()
                    .put(
                            PdfName.T,
                            new PdfString(SCRIBBLE_PREFIX + SCRIBBLE_TOOL_MARK + "LINK_URI"));
            document.addKid(h1);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "ShouldBeList"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc, Scope.TOOL_AUTHORED);

            assertEquals(1, issues.size());
            assertEquals(IssueType.STALE_SCRIBBLE, issues.get(0).type());
        }
    }

    @Test
    void defaultScopeDetectsBothAuthorships() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem h1 = new PdfStructElem(pdfDoc, PdfName.H1, firstPage);
            h1.getPdfObject()
                    .put(
                            PdfName.T,
                            new PdfString(SCRIBBLE_PREFIX + SCRIBBLE_TOOL_MARK + "LINK_URI"));
            document.addKid(h1);

            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P, firstPage);
            p.getPdfObject().put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + "ShouldBeList"));
            document.addKid(p);

            IssueList issues = runCheck(pdfDoc);

            assertEquals(2, issues.size());
        }
    }

    @Test
    void scopeParsesCaseInsensitivelyAndDefaultsToAll() {
        assertEquals(Scope.TOOL_AUTHORED, Scope.parse("tool_authored"));
        assertEquals(Scope.ALL, Scope.parse(null));
        assertThrows(IllegalArgumentException.class, () -> Scope.parse("nonsense"));
    }

    private IssueList runCheck(PdfDocument pdfDoc) {
        return runCheck(pdfDoc, null);
    }

    private IssueList runCheck(PdfDocument pdfDoc, Scope scope) {
        StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
        walker.addVisitor(scope == null ? new StaleScribbleCheck() : new StaleScribbleCheck(scope));
        return walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
    }
}
