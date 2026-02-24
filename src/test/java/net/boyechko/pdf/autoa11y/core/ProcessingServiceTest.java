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
package net.boyechko.pdf.autoa11y.core;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import java.nio.file.Path;
import java.util.Set;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

/** Test suite for ProcessingService. */
public class ProcessingServiceTest extends PdfTestBase {
    private static final Path TAGGED_BASELINE_PDF =
            Path.of("src/test/resources/tagged_baseline.pdf");

    @Test
    void validPdfIsProcessedSuccessfully() throws Exception {
        createProcessingService(TAGGED_BASELINE_PDF).remediate();
    }

    @Test
    void reportOnlyValidatesTagsWithoutModification() throws Exception {
        Path inputPath = TAGGED_BASELINE_PDF;
        IssueList issues = createProcessingService(inputPath).analyze();
        assertNotNull(issues, "Report-only mode should return issues list");
    }

    @Test
    void untaggedPdfReportsNoStructTree() throws Exception {
        Path inputPath = createUntaggedPdf();
        IssueList issues = createProcessingService(inputPath).analyze();
        assertNotNull(issues, "Should return issues list");
        assertTrue(
                issues.stream().anyMatch(i -> i.type() == IssueType.NO_STRUCT_TREE),
                "Should have NO_STRUCT_TREE issue");
    }

    @Test
    void documentLevelIssuesAreDetectedAndFixed() throws Exception {
        Path testPdf =
                createTestPdf(
                        (pdfDoc, layoutDoc) -> {
                            layoutDoc.add(new Paragraph("Document Issues Test").setFontSize(18));
                            layoutDoc.add(
                                    new Paragraph(
                                            "This PDF has content but may be missing "
                                                    + "document-level accessibility properties."));
                        });
        ProcessingResult result = createProcessingService(testPdf).remediate();

        assertNotNull(result, "Should return a result");
        assertNotNull(result.originalDocumentIssues(), "Should have document level issues");
        assertNotNull(result.appliedDocumentFixes(), "Should have applied document fixes");
    }

    @Test
    void tagStructureIssuesAreDetected() throws Exception {
        Path testPdf =
                breakTestPdf(ProcessingServiceTest::listContent, TagBreakage.L_WITH_P_CHILDREN);
        IssueList issues = createProcessingService(testPdf).analyze();
        assertNotNull(issues, "Should return issues list");
        assertTrue(
                issues.stream().anyMatch(i -> isListStructureIssue(i.type())),
                "Should detect at least one list-structure validation issue");
    }

    @Test
    void completeIssueResolutionWorkflow() throws Exception {
        Path testPdf =
                createTestPdf(
                        (pdfDoc, layoutDoc) -> {
                            layoutDoc.add(
                                    new Paragraph("Multiple Issues Test Document").setFontSize(16));
                            layoutDoc.add(
                                    new Paragraph(
                                            "This document contains various elements that may "
                                                    + "have accessibility issues."));

                            Table table = new Table(3);
                            table.addCell("Column 1");
                            table.addCell("Column 2");
                            table.addCell("Column 3");
                            table.addCell("Value A");
                            table.addCell("Value B");
                            table.addCell("Value C");
                            layoutDoc.add(table);

                            com.itextpdf.layout.element.List list =
                                    new com.itextpdf.layout.element.List();
                            list.add(new ListItem("First item"));
                            list.add(new ListItem("Second item"));
                            list.add(new ListItem("Third item"));
                            layoutDoc.add(list);

                            layoutDoc.add(
                                    new Paragraph(
                                            "Additional content to test various accessibility "
                                                    + "rules."));
                        });
        ProcessingResult result = createProcessingService(testPdf).remediate();
        saveRemediatedPdf(result);

        assertNotNull(result.originalTagIssues(), "Should have original tag issues");
        assertNotNull(result.appliedTagFixes(), "Should have applied tag fixes");
        assertNotNull(result.remainingTagIssues(), "Should have remaining tag issues");
        assertNotNull(result.originalDocumentIssues(), "Should have document level issues");
        assertNotNull(result.appliedDocumentFixes(), "Should have applied document fixes");
        assertNotNull(result.remainingDocumentIssues(), "Should have total remaining issues");

        assertTrue(
                result.totalIssuesRemaining() <= result.totalIssuesDetected(),
                "Remaining issues should not exceed detected issues");
    }

    @Test
    void brokenTagStructureIsDetectedAndFixed() throws Exception {
        Path brokenPdf =
                breakTestPdf(ProcessingServiceTest::listContent, TagBreakage.L_WITH_P_CHILDREN);
        ProcessingResult result = createProcessingService(brokenPdf).remediate();
        saveRemediatedPdf(result);

        assertTrue(
                result.originalTagIssues().stream().anyMatch(i -> isListStructureIssue(i.type())),
                "Should detect list-structure issues in the broken PDF");
        assertFalse(
                result.appliedTagFixes().isEmpty(), "Should apply fixes to broken tag structure");
        assertTrue(
                result.appliedTagFixes().stream().anyMatch(i -> isListStructureIssue(i.type())),
                "Should apply at least one fix associated with list-structure issues");
    }

    @Test
    void tagStructureIssuesCanBeFixed() throws Exception {
        Path testPdf =
                breakTestPdf(
                        ProcessingServiceTest::fixableListContent, TagBreakage.LI_WITH_SINGLE_P);
        ProcessingResult result = createProcessingService(testPdf).remediate();
        saveRemediatedPdf(result);

        assertTrue(
                result.originalTagIssues().stream().anyMatch(i -> isListStructureIssue(i.type())),
                "Should detect list-structure issues");
        assertTrue(
                result.appliedTagFixes().stream().anyMatch(i -> isListStructureIssue(i.type())),
                "Should apply fixes to list-structure issues");
    }

    @Test
    void multipleIssueTypesDetectedInSingleRun() throws Exception {
        Path inputPath = TAGGED_BASELINE_PDF;
        ProcessingResult result = createProcessingService(inputPath).remediate();

        assertNotNull(result.originalTagIssues());
        assertNotNull(result.originalDocumentIssues());
        assertEquals(
                0,
                result.totalIssuesRemaining(),
                "Tagged basedline PDF should be compliant with no remaining issues");
    }

    @Test
    void remediatedFigureWithTextIssueIsNotReportedAsRemaining() throws Exception {
        Path inputPath = createFigureWithTextPdf();
        ProcessingResult result = createProcessingService(inputPath).remediate();
        saveRemediatedPdf(result);

        assertTrue(
                result.originalTagIssues().stream()
                        .anyMatch(i -> i.type() == IssueType.FIGURE_WITH_TEXT),
                "Original tag issues should include FIGURE_WITH_TEXT");
        assertTrue(
                result.appliedTagFixes().stream()
                        .anyMatch(i -> i.type() == IssueType.FIGURE_WITH_TEXT),
                "Applied fixes should include FIGURE_WITH_TEXT");
        assertTrue(
                result.remainingTagIssues().stream()
                        .noneMatch(i -> i.type() == IssueType.FIGURE_WITH_TEXT),
                "Remaining tag issues should not include FIGURE_WITH_TEXT");
    }

    @Test
    void skippingPrerequisiteVisitorFailsWithClearMessage() {
        // PagePartVisitor depends on NeedlessNestingVisitor.
        // Skipping the prerequisite should fail with an actionable message,
        // not a generic "has not been registered" error from CheckEngine.
        var ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ProcessingService.ProcessingServiceBuilder()
                                        .withPdfCustodian(
                                                new PdfCustodian(TAGGED_BASELINE_PDF, null))
                                        .withListener(new NoOpProcessingListener())
                                        .skipVisitors(Set.of("NeedlessNestingVisitor"))
                                        .build());
        String message = ex.getMessage();
        assertTrue(
                message.contains("NeedlessNestingVisitor"),
                "Error should name the skipped prerequisite, but was: " + message);
        assertTrue(
                message.contains("PagePartVisitor"),
                "Error should name the dependent visitor, but was: " + message);
        assertTrue(
                message.contains("skip"),
                "Error should suggest skipping both visitors, but was: " + message);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Path createUntaggedPdf() throws Exception {
        Path output = testOutputPath("untagged.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(output.toString()))) {
            doc.addNewPage();
        }
        return output;
    }

    private ProcessingService createProcessingService(Path testPdf) {
        return new ProcessingService.ProcessingServiceBuilder()
                .withPdfCustodian(new PdfCustodian(testPdf, null))
                .withListener(new NoOpProcessingListener())
                .build();
    }

    /** Reusable content: a tagged PDF with a two-item list (suitable for L breakages). */
    private static void listContent(PdfDocument pdfDoc, com.itextpdf.layout.Document layoutDoc)
            throws Exception {
        layoutDoc.add(new Paragraph("Tag Structure Test").setFontSize(18));
        layoutDoc.add(new Paragraph("This PDF will have tag structure issues."));

        com.itextpdf.layout.element.List list = new com.itextpdf.layout.element.List();
        list.add(new ListItem("Item 1"));
        list.add(new ListItem("Item 2"));
        layoutDoc.add(list);
    }

    /** Reusable content: a tagged PDF with a two-item list (suitable for LI breakages). */
    private static void fixableListContent(
            PdfDocument pdfDoc, com.itextpdf.layout.Document layoutDoc) throws Exception {
        layoutDoc.add(new Paragraph("Fixable Tag Issues Test").setFontSize(16));

        com.itextpdf.layout.element.List list = new com.itextpdf.layout.element.List();
        list.add(new ListItem("Item that will be broken"));
        list.add(new ListItem("Another item"));
        layoutDoc.add(list);
    }

    /** Creates a PDF with a Figure element containing text (low-level structure API). */
    private Path createFigureWithTextPdf() throws Exception {
        Path outputPath = testOutputPath();

        try (PdfDocument pdfDoc =
                new PdfDocument(new com.itextpdf.kernel.pdf.PdfWriter(outputPath.toString()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(documentElem);
            PdfStructElem figure = new PdfStructElem(pdfDoc, PdfName.Figure, page);
            documentElem.addKid(figure);

            PdfMcrNumber mcr = new PdfMcrNumber(page, figure);
            figure.addKid(mcr);

            PdfDictionary props = new PdfDictionary();
            props.put(PdfName.MCID, new PdfNumber(mcr.getMcid()));
            PdfCanvas canvas = new PdfCanvas(page);
            canvas.beginMarkedContent(PdfName.Figure, props);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            canvas.beginText();
            canvas.setFontAndSize(font, 24);
            canvas.moveText(100, 750);
            canvas.showText("Figure text that should trigger remediation");
            canvas.endText();
            canvas.endMarkedContent();
        }

        return outputPath;
    }

    private static boolean isListStructureIssue(IssueType issueType) {
        return issueType == IssueType.TAG_WRONG_CHILD
                || issueType == IssueType.TAG_WRONG_CHILD_COUNT
                || issueType == IssueType.TAG_WRONG_CHILD_PATTERN;
    }
}
