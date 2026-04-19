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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class InvalidLinkUriCheckTest extends PdfTestBase {

    @Test
    void flagsLinkWithInvalidUri() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructElem link = buildLinkWithUri(pdfDoc, "https://practice.10");

            IssueList issues = runCheck(pdfDoc);

            assertEquals(1, issues.size(), "Expected one invalid-URI issue");
            Issue issue = issues.get(0);
            assertEquals(IssueType.INVALID_LINK_URI, issue.type());

            DocValue.Scribble scribble = DocValue.Scribble.of(link);
            assertNotNull(scribble, "Expected a scribble on the Link element");
            assertTrue(
                    scribble.value().startsWith(InvalidLinkUriCheck.CHECK_SCRIBBLE_PREFIX),
                    "Scribble should start with check prefix");
            assertTrue(
                    scribble.value().contains("https://practice.10"),
                    "Scribble should include offending URI");
        }
    }

    @Test
    void ignoresLinkWithValidUri() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            buildLinkWithUri(pdfDoc, "https://example.com/page");

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size(), "Valid URI should produce no issues");
        }
    }

    @Test
    void ignoresLinkWithoutUriAction() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"), page);
            document.addKid(p);

            // Link annotation with no /A action at all.
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14));
            page.addAnnotation(-1, annot, false);

            PdfStructElem linkElem = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(linkElem);
            int spi = pdfDoc.getNextStructParentIndex();
            linkElem.addKid(new PdfObjRef(annot, linkElem, spi));

            IssueList issues = runCheck(pdfDoc);

            assertEquals(
                    0, issues.size(), "Links with no URI action are out of this check's scope");
        }
    }

    @Test
    void ignoresLinkWithInternalDestination() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"), page);
            document.addKid(p);

            // Link annotation with an explicit /Dest [page /XYZ x y zoom] — typical TOC link.
            PdfLinkAnnotation annot = new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14));
            PdfArray dest = new PdfArray();
            dest.add(page.getPdfObject());
            dest.add(PdfName.XYZ);
            dest.add(new PdfNumber(100));
            dest.add(new PdfNumber(700));
            dest.add(new PdfNumber(0));
            annot.getPdfObject().put(PdfName.Dest, dest);
            page.addAnnotation(-1, annot, false);

            PdfStructElem linkElem = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(linkElem);
            int spi = pdfDoc.getNextStructParentIndex();
            linkElem.addKid(new PdfObjRef(annot, linkElem, spi));

            IssueList issues = runCheck(pdfDoc);

            assertEquals(0, issues.size(), "Internal-destination Links should not be flagged");
        }
    }

    private PdfStructElem buildLinkWithUri(PdfDocument pdfDoc, String uri) {
        pdfDoc.setTagged();
        PdfPage page = pdfDoc.addNewPage();

        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        root.addKid(document);
        PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"), page);
        document.addKid(p);

        PdfLinkAnnotation annot =
                new PdfLinkAnnotation(new Rectangle(100, 700, 60, 14))
                        .setAction(PdfAction.createURI(uri));
        page.addAnnotation(-1, annot, false);

        PdfStructElem linkElem = new PdfStructElem(pdfDoc, PdfName.Link, page);
        p.addKid(linkElem);
        int spi = pdfDoc.getNextStructParentIndex();
        linkElem.addKid(new PdfObjRef(annot, linkElem, spi));
        return linkElem;
    }

    private IssueList runCheck(PdfDocument pdfDoc) {
        DocContext ctx = new DocContext(pdfDoc);
        StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
        walker.addVisitor(new InvalidLinkUriCheck());
        return walker.walk(pdfDoc.getStructTreeRoot(), ctx);
    }
}
