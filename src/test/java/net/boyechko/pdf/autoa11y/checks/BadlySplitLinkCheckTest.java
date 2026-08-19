// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class BadlySplitLinkCheckTest extends PdfTestBase {

    private static final String SHARED_URI = "https://uw.edu/catalog/Education-875.html";

    private static PdfStructElem paragraphOf(PdfDocument pdfDoc) {
        PdfStructElem document = StructTree.findDocument(pdfDoc.getStructTreeRoot());
        return StructTree.findFirstChild(document, PdfName.P);
    }

    private static void check(PdfDocument pdfDoc, BadlySplitLinkCheck check) {
        check.findIssues(new DocContext(pdfDoc));
    }

    @Test
    void detectsAdjacentLinksSharingOneDestination() throws Exception {
        BadlySplitLinkCheck check = new BadlySplitLinkCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Program of Study: Major:", PdfAction.createURI(SHARED_URI)));
            p.add(new Link("Developmental and Youth Studies", PdfAction.createURI(SHARED_URI)));
            layoutDoc.add(p);

            check(pdfDoc, check);

            assertEquals(1, check.getIssues().size(), "Should have 1 issue");
            assertEquals(IssueType.LINK_SPLIT_ACROSS_TAGS, check.getIssues().get(0).type());
        }
    }

    @Test
    void ignoresAdjacentLinksWithDistinctDestinations() throws Exception {
        BadlySplitLinkCheck check = new BadlySplitLinkCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Autumn 2024", PdfAction.createURI("https://uw.edu/AUT2024/acmpt")));
            p.add(new Link("Winter 2025", PdfAction.createURI("https://uw.edu/WIN2025/acmpt")));
            layoutDoc.add(p);

            check(pdfDoc, check);

            assertTrue(check.getIssues().isEmpty(), "Should have no issues");
        }
    }

    @Test
    void ignoresLinksAgreeingOnActionButNotOnOriginalUri() throws Exception {
        // Same target page, different Web Capture anchors: two distinct links, not one split link.
        BadlySplitLinkCheck check = new BadlySplitLinkCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Undergraduate Programs", PdfAction.createURI(SHARED_URI)));
            p.add(new Link("Graduate Programs", PdfAction.createURI(SHARED_URI)));
            layoutDoc.add(p);

            List<PdfAnnotation> annots = pdfDoc.getPage(1).getAnnotations();
            assertEquals(2, annots.size(), "Expected one annotation per link");
            setOriginalUri(annots.get(0), SHARED_URI + "#undergradPrograms");
            setOriginalUri(annots.get(1), SHARED_URI + "#gradPrograms");

            check(pdfDoc, check);

            assertTrue(check.getIssues().isEmpty(), "Should have no issues");
        }
    }

    @Test
    void ignoresLinksSeparatedByOtherContent() throws Exception {
        // Anything between the Links -- a Span here, a bare text MCR in a real document --
        // means they delimit distinct phrases, and merging them would swallow it.
        BadlySplitLinkCheck check = new BadlySplitLinkCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("this page", PdfAction.createURI(SHARED_URI)));
            p.add(new Text(" or "));
            p.add(new Link("that page", PdfAction.createURI(SHARED_URI)));
            layoutDoc.add(p);

            assertEquals(
                    "P[Link[], Span[], Link[]]",
                    StructTree.toRoleTree(paragraphOf(pdfDoc)).toString(),
                    "Expected the two Link tags to be separated by the intervening text");

            check(pdfDoc, check);

            assertTrue(check.getIssues().isEmpty(), "Should have no issues");
        }
    }

    @Test
    void reportsOneIssuePerRunOfSharedDestination() throws Exception {
        // Two split links back to back: two merges, not one run of four.
        BadlySplitLinkCheck check = new BadlySplitLinkCheck();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            Paragraph p = new Paragraph();
            p.add(new Link("Program of ", PdfAction.createURI(SHARED_URI)));
            p.add(new Link("Study", PdfAction.createURI(SHARED_URI)));
            p.add(new Link("Back to ", PdfAction.createURI("https://uw.edu/catalog#top")));
            p.add(new Link("top", PdfAction.createURI("https://uw.edu/catalog#top")));
            layoutDoc.add(p);

            assertEquals(
                    4,
                    StructTree.childrenOf(paragraphOf(pdfDoc), PdfStructElem.class).size(),
                    "Expected four adjacent Link tags");

            check(pdfDoc, check);

            assertEquals(2, check.getIssues().size(), "Should have 2 issues");
        }
    }

    /** Records a Web Capture original URI in an annotation's /PA entry. */
    private static void setOriginalUri(PdfAnnotation annot, String uri) {
        annot.getPdfObject().put(PdfName.PA, PdfAction.createURI(uri).getPdfObject());
    }
}
