// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class InconsistentParentTreeCheckTest extends PdfTestBase {

    @Test
    void detectsMcidMissingFromParentTree() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            buildDocWithUnregisteredMcid(pdfDoc, 0);

            IssueList issues = new InconsistentParentTreeCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(1, issues.size());
            assertEquals(IssueType.INCONSISTENT_PARENT_TREE, issues.get(0).type());
            assertNotNull(issues.get(0).fix());
        }
    }

    @Test
    void passesWhenMcidsAreProperlyRegistered() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(p);
            p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            p.addKid(new PdfMcrNumber(new PdfNumber(0), p)); // registered via addKid

            IssueList issues = new InconsistentParentTreeCheck().findIssues(new DocContext(pdfDoc));

            assertTrue(issues.isEmpty());
        }
    }

    @Test
    void fixReregistersMcidUnderItsElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfStructElem p = buildDocWithUnregisteredMcid(pdfDoc, 0);
            PdfPage page = pdfDoc.getPage(1);
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            assertNull(root.findMcrByMcid(page.getPdfObject(), 0), "precondition: not registered");

            DocContext ctx = new DocContext(pdfDoc);
            Issue issue = new InconsistentParentTreeCheck().findIssues(ctx).get(0);
            issue.fix().apply(ctx);

            PdfMcr registered = root.findMcrByMcid(page.getPdfObject(), 0);
            assertNotNull(registered, "MCID 0 should now resolve through the ParentTree");
            assertSame(p.getPdfObject(), ((PdfStructElem) registered.getParent()).getPdfObject());
        }
    }

    @Test
    void detachedOwnerNoLongerShadowsLiveOwnerAfterReopen() throws Exception {
        // Round-trips through bytes: iText seeds its MCR registry from the stored ParentTree on
        // load, so a detached owner only reasserts its claim when the file is reopened.
        ByteArrayOutputStream corrupt = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(corrupt))) {
            PdfStructElem p = buildDocWithUnregisteredMcid(pdfDoc, 0);
            PdfStructElem document = (PdfStructElem) p.getParent();
            PdfStructElem detached = new PdfStructElem(pdfDoc, PdfName.Art);
            document.addKid(detached);
            detached.getPdfObject().put(PdfName.Pg, pdfDoc.getPage(1).getPdfObject());
            detached.addKid(new PdfMcrNumber(new PdfNumber(0), detached)); // registered
            // A claim no live element shares keeps the Art named in the ParentTree even after
            // MCID 0 is re-registered under P, so the Art re-seeds (and wins) on every reopen.
            detached.addKid(new PdfMcrNumber(new PdfNumber(1), detached));
            document.getPdfObject().put(PdfName.K, p.getPdfObject()); // raw: Art now unreachable
        }

        ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc =
                new PdfDocument(
                        new PdfReader(new ByteArrayInputStream(corrupt.toByteArray())),
                        new PdfWriter(fixed))) {
            DocContext ctx = new DocContext(pdfDoc);
            IssueList issues = new InconsistentParentTreeCheck().findIssues(ctx);
            assertFalse(issues.isEmpty(), "detached owner should be detected");
            for (Issue issue : issues) issue.fix().apply(ctx);
        }

        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(fixed.toByteArray())))) {
            PdfMcr registered =
                    pdfDoc.getStructTreeRoot().findMcrByMcid(pdfDoc.getPage(1).getPdfObject(), 0);
            assertNotNull(registered);
            assertEquals(PdfName.P, ((PdfStructElem) registered.getParent()).getRole());
            assertTrue(
                    new InconsistentParentTreeCheck().findIssues(new DocContext(pdfDoc)).isEmpty());
        }
    }

    /**
     * Builds Document > P where P carries a bare-int MCID written straight into /K (bypassing
     * addKid), so the ParentTree never learns about it -- the corrupt state this check targets.
     */
    private static PdfStructElem buildDocWithUnregisteredMcid(PdfDocument pdfDoc, int mcid) {
        pdfDoc.setTagged();
        PdfPage page = pdfDoc.addNewPage();
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
        root.addKid(document);
        PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
        document.addKid(p);
        p.getPdfObject().put(PdfName.Pg, page.getPdfObject());
        PdfArray k = new PdfArray();
        k.add(new PdfNumber(mcid));
        p.getPdfObject().put(PdfName.K, k); // raw write: no ParentTree registration
        return p;
    }
}
