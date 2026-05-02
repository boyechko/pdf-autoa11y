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

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.InlineDestinationsFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class InlineDestinationsCheckTest extends PdfTestBase {

    @Test
    void noIssuesWhenDestsTreeIsEmpty() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();

            IssueList issues = new InlineDestinationsCheck().findIssues(new DocContext(pdfDoc));

            assertTrue(issues.isEmpty(), "Empty /Dests tree should produce no issues");
        }
    }

    @Test
    void flagsSinglePresentNamedDestination() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();
            addNamedFitDestination(pdfDoc, "MyChapter", page);

            IssueList issues = new InlineDestinationsCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(1, issues.size());
            assertEquals(IssueType.NAMED_DESTINATIONS_PRESENT, issues.get(0).type());
            assertInstanceOf(InlineDestinationsFix.class, issues.get(0).fix());
        }
    }

    @Test
    void issueMessageReportsCount() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            PdfPage page = pdfDoc.addNewPage();
            addNamedFitDestination(pdfDoc, "Chapter1", page);
            addNamedFitDestination(pdfDoc, "Chapter2", page);
            addNamedFitDestination(pdfDoc, "Chapter3", page);

            IssueList issues = new InlineDestinationsCheck().findIssues(new DocContext(pdfDoc));

            assertEquals(1, issues.size());
            assertTrue(
                    issues.get(0).message().contains("3"),
                    "Message should report the destination count: " + issues.get(0).message());
        }
    }

    /** Adds a fit-page named destination to the catalog's /Names /Dests tree. */
    private static void addNamedFitDestination(PdfDocument pdfDoc, String name, PdfPage page) {
        PdfArray destArray = new PdfArray();
        destArray.add(page.getPdfObject());
        destArray.add(PdfName.Fit);
        pdfDoc.getCatalog().getNameTree(PdfName.Dests).addEntry(name, destArray);
    }
}
