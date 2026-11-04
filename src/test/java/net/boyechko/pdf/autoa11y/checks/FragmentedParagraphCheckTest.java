// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class FragmentedParagraphCheckTest extends PdfTestBase {

    /**
     * Lays out each string as its own tagged P under Document and runs the check on the saved file,
     * whose content streams text extraction can read.
     */
    private IssueList checkParagraphs(String... texts) throws Exception {
        Path pdfFile = testOutputPath();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(pdfFile.toString()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            for (String text : texts) {
                layoutDoc.add(new Paragraph(text));
            }
        }
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            return new FragmentedParagraphCheck().findIssues(new DocContext(pdfDoc));
        }
    }

    @Test
    void detectsParagraphContinuedInNextParagraph() throws Exception {
        IssueList issues =
                checkParagraphs(
                        "Transfer students with 45+ credits may petition to",
                        "opt out of Discovery Core.");

        assertEquals(1, issues.size(), "Should have 1 issue");
        assertEquals(IssueType.PARAGRAPH_FRAGMENTED, issues.get(0).type());
    }

    @Test
    void reportsOneIssuePerRunOfFragments() throws Exception {
        IssueList issues =
                checkParagraphs(
                        "Transfer students with 45+ credits may petition to",
                        "opt out of Discovery Core and enroll in an BCORE",
                        "course.",
                        "Learn more about Discovery Core.",
                        "Students who have already",
                        "enrolled may skip this step.");

        assertEquals(2, issues.size(), "Should have 2 issues");
    }

    @Test
    void ignoresParagraphEndingInTerminalPunctuation() throws Exception {
        IssueList issues =
                checkParagraphs(
                        "The following courses satisfy the requirement:",
                        "any course with the BCORE prefix.");

        assertTrue(issues.isEmpty(), "Should have no issues");
    }

    @Test
    void ignoresNextParagraphStartingWithCapital() throws Exception {
        IssueList issues = checkParagraphs("Contact FYPP", "Location: UW1-160, Bothell Campus");

        assertTrue(issues.isEmpty(), "Should have no issues");
    }

    @Test
    void ignoresLetteredItems() throws Exception {
        IssueList issues = checkParagraphs("a. Development", "b. Design", "c) Foundations");

        assertTrue(issues.isEmpty(), "Should have no issues");
    }

    @Test
    void ignoresNextParagraphStartingWithEmailOrUrl() throws Exception {
        IssueList issues =
                checkParagraphs(
                        "UW1 Campus Building 425-352-5376",
                        "uwbnhs@uw.edu",
                        "Office of Financial Aid",
                        "www.fafsa.ed.gov",
                        "Office of Admissions",
                        "https://www.uwb.edu/admissions");

        assertTrue(issues.isEmpty(), "Should have no issues");
    }
}
