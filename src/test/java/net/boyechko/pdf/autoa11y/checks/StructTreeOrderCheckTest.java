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
import java.util.HashMap;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.StructTreeOrderFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
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

    @Test
    void fixReordersChildrenToMatchReadingOrder() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfPage page3 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Add in reverse page order: page3, page2, page1
            PdfStructElem p3 = new PdfStructElem(doc, PdfName.P, page3);
            PdfStructElem p2 = new PdfStructElem(doc, PdfName.P, page2);
            PdfStructElem p1 = new PdfStructElem(doc, PdfName.P, page1);
            document.addKid(p3);
            document.addKid(p2);
            document.addKid(p1);

            addMcr(page3, p3);
            addMcr(page2, p2);
            addMcr(page1, p1);

            DocContext ctx = new DocContext(doc);

            // Detect and apply per-element fixes
            IssueList issues = new StructTreeOrderCheck().findIssues(ctx);
            assertFalse(issues.isEmpty(), "Precondition: should detect out-of-order");
            applyAllFixes(issues, ctx);

            // Verify children are now in page order
            List<PdfStructElem> kids = StructTree.childrenOf(document, PdfStructElem.class);
            assertEquals(3, kids.size());
            assertTrue(
                    StructTree.isSameElement(kids.get(0), p1),
                    "First child should be page-1 element");
            assertTrue(
                    StructTree.isSameElement(kids.get(1), p2),
                    "Second child should be page-2 element");
            assertTrue(
                    StructTree.isSameElement(kids.get(2), p3),
                    "Third child should be page-3 element");

            // Re-check: should now be clean
            IssueList afterIssues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(afterIssues.isEmpty(), "After fix, no order issues should remain");
        }
    }

    @Test
    void fixIsIdempotentOnAlreadyOrderedElement() throws Exception {
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

            // Directly apply a fix to an already-ordered element
            DocContext ctx = new DocContext(doc);
            new StructTreeOrderFix(document, new HashMap<>()).apply(ctx);

            // Order should be unchanged
            List<PdfStructElem> kids = StructTree.childrenOf(document, PdfStructElem.class);
            assertTrue(StructTree.isSameElement(kids.get(0), p1));
            assertTrue(StructTree.isSameElement(kids.get(1), p2));
        }
    }

    @Test
    void multipleOutOfOrderParentsProduceMultipleIssues() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            PdfPage page1 = doc.addNewPage();
            PdfPage page2 = doc.addNewPage();
            PdfPage page3 = doc.addNewPage();
            PdfPage page4 = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);

            // Sect1: children out of order (page2 before page1)
            PdfStructElem sect1 = new PdfStructElem(doc, PdfName.Sect, page1);
            document.addKid(sect1);
            PdfStructElem s1p2 = new PdfStructElem(doc, PdfName.P, page2);
            PdfStructElem s1p1 = new PdfStructElem(doc, PdfName.P, page1);
            sect1.addKid(s1p2);
            sect1.addKid(s1p1);
            addMcr(page2, s1p2);
            addMcr(page1, s1p1);

            // Sect2: children out of order (page4 before page3)
            PdfStructElem sect2 = new PdfStructElem(doc, PdfName.Sect, page3);
            document.addKid(sect2);
            PdfStructElem s2p4 = new PdfStructElem(doc, PdfName.P, page4);
            PdfStructElem s2p3 = new PdfStructElem(doc, PdfName.P, page3);
            sect2.addKid(s2p4);
            sect2.addKid(s2p3);
            addMcr(page4, s2p4);
            addMcr(page3, s2p3);

            IssueList issues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            long orderIssues =
                    issues.stream()
                            .filter(i -> i.type() == IssueType.STRUCT_TREE_OUT_OF_ORDER)
                            .count();
            assertEquals(2, orderIssues, "Each out-of-order parent should produce its own issue");

            // Apply all fixes and verify
            DocContext ctx = new DocContext(doc);
            applyAllFixes(issues, ctx);

            IssueList afterIssues = new StructTreeOrderCheck().findIssues(new DocContext(doc));
            assertTrue(afterIssues.isEmpty(), "All order issues should be resolved");
        }
    }

    /** Adds a simple MCR to a structure element on the given page. */
    private void addMcr(PdfPage page, PdfStructElem elem) {
        PdfMcrNumber mcr = new PdfMcrNumber(page, elem);
        elem.addKid(mcr);
    }

    /** Applies all fixes from the issue list. */
    private void applyAllFixes(IssueList issues, DocContext ctx) throws Exception {
        for (Issue issue : issues) {
            IssueFix fix = issue.fix();
            if (fix != null) {
                fix.apply(ctx);
            }
        }
    }
}
