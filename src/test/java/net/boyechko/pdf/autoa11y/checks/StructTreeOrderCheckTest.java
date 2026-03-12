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
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class StructTreeOrderCheckTest extends PdfTestBase {

    @Test
    void elementsInPageOrderProducesNoIssues() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            document.addKid(p1);
            document.addKid(p2);

            addMcr(page1, p1);
            addMcr(page2, p2);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "Elements in page order should produce no issues");
        }
    }

    @Test
    void elementsOutOfPageOrderDetected() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Add in reverse order: page2 element first, then page1
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            document.addKid(p2);
            document.addKid(p1);

            addMcr(page2, p2);
            addMcr(page1, p1);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertFalse(issues.isEmpty(), "Out-of-order elements should be detected");
            assertEquals(
                    IssueType.STRUCT_TREE_OUT_OF_ORDER,
                    issues.stream().findFirst().orElseThrow().type());
        }
    }

    @Test
    void nestedOutOfOrderDetected() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfPage page3 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Two Sect elements in correct order
            PdfStructElem sect1 = new PdfStructElem(doc, PdfName.Sect, page1);
            PdfStructElem sect2 = new PdfStructElem(doc, PdfName.Sect, page2);
            document.addKid(sect1);
            document.addKid(sect2);

            addMcr(page1, sect1);

            // Within sect2, children are reversed: page3 before page2
            PdfStructElem p3 = new PdfStructElem(doc, PdfName.P, page3);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            sect2.addKid(p3);
            sect2.addKid(p2);

            addMcr(page3, p3);
            addMcr(page2, p2);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertEquals(
                    1,
                    issues.stream()
                            .filter(i -> i.type() == IssueType.STRUCT_TREE_OUT_OF_ORDER)
                            .count(),
                    "Only sect2 has out-of-order children; Document level is correctly ordered");
        }
    }

    @Test
    void singleChildProducesNoIssues() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            document.addKid(p1);

            addMcr(page1, p1);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(issues.isEmpty(), "Single child should not trigger order check");
        }
    }

    @Test
    void samePageDifferentMcidsOutOfOrder() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // pEarlier should appear first (lower mcid), pLater second
            PdfStructElem pEarlier = new PdfStructElem(doc, PdfName.P, page1);
            PdfStructElem pLater = new PdfStructElem(doc, PdfName.P, page1);

            // Add to tree in wrong order: pLater first, pEarlier second
            document.addKid(pLater);
            document.addKid(pEarlier);

            // pEarlier gets mcid 0 (first on page), pLater gets mcid 1
            addMcr(page1, pEarlier);
            addMcr(page1, pLater);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertFalse(
                    issues.isEmpty(),
                    "Same-page elements with reversed MCIDs should be out of order");
        }
    }

    /** Adds a simple MCR to a structure element on the given page. */
    private void addMcr(PdfPage page, PdfStructElem elem) {
        PdfMcrNumber mcr = new PdfMcrNumber(page, elem);
        elem.addKid(mcr);
    }
}
