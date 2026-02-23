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
package net.boyechko.pdf.autoa11y.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfConformance;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfUAConformance;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import java.lang.reflect.Field;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueSev;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;

class PdfUaConformanceRuleTest extends PdfTestBase {

    private final PdfUaConformanceRule rule = new PdfUaConformanceRule();

    @Test
    void detectsFalsePdfUaClaim() throws Exception {
        Path pdfPath = createPdfClaimingUa();

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            DocumentContext ctx = new DocumentContext(doc);
            IssueList issues = rule.findIssues(ctx);

            assertEquals(1, issues.size(), "Should detect false PDF/UA claim");
            Issue issue = issues.get(0);
            assertEquals(IssueType.FALSE_PDFUA_CONFORMANCE, issue.type());
            assertEquals(IssueSev.ERROR, issue.severity());
            assertNotNull(issue.fix(), "Should have a fix");
        }
    }

    @Test
    void fixStripsPdfUaClaim() throws Exception {
        Path pdfPath = createPdfClaimingUa();
        Path fixedPath = testOutputPath("fixed.pdf");

        // Apply the fix
        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(pdfPath.toString()), new PdfWriter(fixedPath.toString()))) {
            DocumentContext ctx = new DocumentContext(doc);
            IssueList issues = rule.findIssues(ctx);
            assertEquals(1, issues.size());
            issues.get(0).fix().apply(ctx);
        }

        // Verify the claim is gone
        try (PdfDocument result = new PdfDocument(new PdfReader(fixedPath.toString()))) {
            assertNull(
                    result.getConformance().getUAConformance(),
                    "PDF/UA claim should be stripped after fix");
        }
    }

    @Test
    void passesWhenNoClaim() throws Exception {
        Path pdfPath = testOutputPath("no_claim.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfPath.toString()))) {
            doc.addNewPage();
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            DocumentContext ctx = new DocumentContext(doc);
            IssueList issues = rule.findIssues(ctx);
            assertTrue(issues.isEmpty(), "Should pass when no PDF/UA claim");
        }
    }

    /** Canary: fails if iText renames the field we clear via reflection. */
    @Test
    void pdfDocumentHasPdfConformanceField() throws Exception {
        Field field = PdfDocument.class.getDeclaredField("pdfConformance");
        assertEquals(
                PdfConformance.class,
                field.getType(),
                "PdfDocument.pdfConformance field type changed — update"
                        + " PdfUaConformanceRule.clearCachedConformance()");
    }

    private Path createPdfClaimingUa() throws Exception {
        Path pdfPath = testOutputPath("claims_ua.pdf");
        WriterProperties props = new WriterProperties();
        props.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdfPath.toString(), props))) {
            doc.addNewPage();
        }
        return pdfPath;
    }
}
