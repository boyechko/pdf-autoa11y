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
