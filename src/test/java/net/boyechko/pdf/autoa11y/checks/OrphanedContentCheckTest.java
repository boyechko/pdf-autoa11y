// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.junit.jupiter.api.Test;

class OrphanedContentCheckTest extends PdfTestBase {

    /*
     * An MCR is "orphaned" when iText's PdfMcr.getPageIndirectReference() returns null.
     * The iText impl only checks the MCR's own dict and its immediate parent struct
     * elem for /Pg -- so attaching a bare-int MCR to a struct elem that has no /Pg
     * synthesizes the same state Acrobat leaves behind when pages are deleted without
     * cleaning up tags.
     */

    @Test
    void noIssuesOnValidTree() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(TAGGED_BASELINE_PDF.toString()))) {
            IssueList issues = new OrphanedContentCheck().findIssues(new DocContext(doc));
            assertEquals(0, issues.size());
        }
    }

    @Test
    void detectsOrphanedMcr() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfMcrNumber mcr = new PdfMcrNumber(new PdfNumber(0), p);
            p.addKid(mcr);

            assertNull(mcr.getPageIndirectReference(), "preconditions: MCR is orphaned");

            IssueList issues = new OrphanedContentCheck().findIssues(new DocContext(doc));
            assertEquals(1, issues.size());
        }
    }

    @Test
    void detectsOrphanedObjr() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem link = new PdfStructElem(doc, PdfName.Link);
            document.addKid(link);

            PdfDictionary objRefDict = new PdfDictionary();
            objRefDict.put(PdfName.Type, PdfName.OBJR);
            objRefDict.put(PdfName.Obj, new PdfDictionary());
            PdfObjRef objRef = new PdfObjRef(objRefDict, link);
            link.addKid(objRef);

            assertNull(objRef.getPageIndirectReference(), "preconditions: OBJR is orphaned");

            IssueList issues = new OrphanedContentCheck().findIssues(new DocContext(doc));
            assertEquals(1, issues.size());
        }
    }

    @Test
    void doesNotFlagMcrWithValidPageRef() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            var page = doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P, page);
            document.addKid(p);
            p.addKid(new PdfMcrNumber(page, p));

            IssueList issues = new OrphanedContentCheck().findIssues(new DocContext(doc));
            assertEquals(0, issues.size());
        }
    }

    @Test
    void emitsOneIssuePerOrphan() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            p.addKid(new PdfMcrNumber(new PdfNumber(0), p));
            p.addKid(new PdfMcrNumber(new PdfNumber(1), p));
            p.addKid(new PdfMcrNumber(new PdfNumber(2), p));

            IssueList issues = new OrphanedContentCheck().findIssues(new DocContext(doc));
            assertEquals(3, issues.size());
        }
    }
}
