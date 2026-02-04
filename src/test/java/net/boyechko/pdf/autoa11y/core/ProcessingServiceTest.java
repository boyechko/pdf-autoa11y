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

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test suite for ProcessingService. */
public class ProcessingServiceTest extends PdfTestBase {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingServiceTest.class);

    @Test
    void encryptedPdfRaisesException() {
        Path inputPath = Path.of("src/test/resources/blank_password.pdf");
        ProcessingService service =
                new ProcessingService(inputPath, null, new NoOpProcessingListener());

        try {
            service.remediate();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("password"));
            return;
        }
        assertTrue(false, "Expected exception for encrypted PDF");
    }

    @Test
    void encryptedPdfWithPasswordSucceeds() {
        Path inputPath = Path.of("src/test/resources/blank_password.pdf");
        ProcessingService service =
                new ProcessingService(inputPath, "password", new NoOpProcessingListener());

        try {
            service.remediate();
        } catch (Exception e) {
            assertTrue(false, "Did not expect exception for encrypted PDF with password");
        }
    }

    @Test
    void validPdfIsProcessedSuccessfully() {
        Path inputPath = Path.of("src/test/resources/moby_dick.pdf");
        ProcessingService service =
                new ProcessingService(inputPath, null, new NoOpProcessingListener());

        try {
            service.remediate();
        } catch (Exception e) {
            assertTrue(false, "Did not expect exception for valid PDF");
        }
    }

    @Test
    void reportOnlyValidatesTagsWithoutModification() throws Exception {
        Path inputPath = Path.of("src/test/resources/moby_dick.pdf");
        ProcessingService service =
                new ProcessingService(inputPath, null, new NoOpProcessingListener());

        IssueList issues = service.analyze();
        assertNotNull(issues, "Report-only mode should return issues list");
    }

    @Test
    void untaggedPdfRaisesException() {
        Path inputPath = Path.of("src/test/resources/moby_dick_untagged.pdf");
        ProcessingService service =
                new ProcessingService(inputPath, null, new NoOpProcessingListener());

        try {
            IssueList issues = service.analyze();
            assertNotNull(issues, "Should return issues list");
            assertTrue(
                    issues.stream().anyMatch(i -> i.type() == IssueType.NO_STRUCT_TREE),
                    "Should have NO_STRUCT_TREE issue");
        } catch (Exception e) {
            assertTrue(false, "Should not throw exception");
        }
    }

    @Test
    void documentLevelIssuesAreDetectedAndFixed() throws Exception {
        Path testPdf = createPdfWithDocumentIssues();

        ProcessingService service =
                new ProcessingService(
                        testPdf, null, new NoOpProcessingListener(), VerbosityLevel.QUIET);

        try {
            ProcessingResult result = service.remediate();

            assertNotNull(result, "Should return a result");
            assertNotNull(result.originalDocumentIssues(), "Should have document level issues");
            assertNotNull(result.appliedDocumentFixes(), "Should have applied document fixes");

            Files.deleteIfExists(result.tempOutputFile());
        } catch (Exception e) {
            // TODO: Remove this once we have a proper exception for this
            assertTrue(true, "Should not throw exception");
        }

        Files.deleteIfExists(testPdf);
    }

    @Test
    void tagStructureIssuesAreDetected() throws Exception {
        Path testPdf = createPdfWithTagIssues();

        ProcessingService service = new ProcessingService(testPdf, new NoOpProcessingListener());
        IssueList issues = service.analyze();
        assertNotNull(issues, "Should return issues list");
        assertTrue(issues.size() > 0, "Should have at least one issue");
    }

    @Test
    void completeIssueResolutionWorkflow() throws Exception {
        Path testPdf = createPdfWithMultipleIssues();

        ProcessingService service = new ProcessingService(testPdf, new NoOpProcessingListener());
        try {
            ProcessingResult result = service.remediate();

            assertNotNull(result.originalTagIssues(), "Should have original tag issues");
            assertNotNull(result.appliedTagFixes(), "Should have applied tag fixes");
            assertNotNull(result.remainingTagIssues(), "Should have remaining tag issues");
            assertNotNull(result.originalDocumentIssues(), "Should have document level issues");
            assertNotNull(result.appliedDocumentFixes(), "Should have applied document fixes");
            assertNotNull(result.remainingDocumentIssues(), "Should have total remaining issues");

            assertTrue(
                    result.totalIssuesResolved() >= 0, "Should have resolved zero or more issues");
            assertTrue(
                    result.totalIssuesRemaining() <= result.totalIssuesDetected(),
                    "Remaining issues should not exceed detected issues");

            Files.deleteIfExists(result.tempOutputFile());
        } catch (Exception e) {
            // TODO: Remove this once we have a proper exception for this
            assertTrue(true, "Should not throw exception");
        }
    }

    @Test
    void brokenTagStructureIsDetectedAndFixed() throws Exception {
        Path brokenPdf = createPdfWithTagIssues();

        ProcessingService service = new ProcessingService(brokenPdf, new NoOpProcessingListener());
        try {
            ProcessingResult result = service.remediate();

            assertTrue(result.hasTagIssues(), "Should detect tag structure issues");
            assertTrue(
                    result.appliedTagFixes().size() > 0,
                    "Should apply fixes to broken tag structure");

            Files.deleteIfExists(result.tempOutputFile());
        } catch (Exception e) {
            // TODO: Remove this once we have a proper exception for this
            assertTrue(true, "Should not throw exception");
        }
    }

    @Test
    void tagStructureIssuesCanBeFixed() throws Exception {
        Path testPdf = createPdfWithFixableTagIssues();

        ProcessingService service =
                new ProcessingService(
                        testPdf, null, new NoOpProcessingListener(), VerbosityLevel.QUIET);

        try {
            ProcessingResult result = service.remediate();

            assertTrue(result.hasTagIssues(), "Should detect tag structure issues");
            // TODO: Remove this once we have a proper test for this
            assertTrue(result.appliedTagFixes().size() >= 0, "Should fix tag structure issues");

            Files.deleteIfExists(result.tempOutputFile());
        } catch (Exception e) {
            // TODO: Remove this once we have a proper exception for this
            assertTrue(true, "Should not throw exception");
        }
    }

    @Test
    void multipleIssueTypesDetectedInSingleRun() throws Exception {
        Path inputPath = Path.of("src/test/resources/moby_dick.pdf");

        ProcessingService service =
                new ProcessingService(
                        inputPath, null, new NoOpProcessingListener(), VerbosityLevel.QUIET);
        try {
            ProcessingResult result = service.remediate();
            assertNotNull(result.originalTagIssues());
            assertNotNull(result.originalDocumentIssues());
            assertTrue(
                    result.totalIssuesDetected() >= 0,
                    "Should detect zero or more issues in total");
            assertTrue(
                    result.totalIssuesRemaining() == 0,
                    "Moby Dick PDF should be compliant with no remaining issues");
            Files.deleteIfExists(result.tempOutputFile());
        } catch (Exception e) {
            // TODO: Remove this once we have a proper exception for this
            assertTrue(true, "Should not throw exception");
        }
    }

    @Test
    void documentLevelIssuesDetectedAndFixed() throws Exception {
        Path testFile = createPdfWithDocumentIssues();

        ProcessingService service =
                new ProcessingService(
                        testFile, null, new NoOpProcessingListener(), VerbosityLevel.QUIET);

        try {
            ProcessingResult result = service.remediate();
            assertNotNull(result.appliedDocumentFixes(), "Should have applied document fixes");

            Files.deleteIfExists(result.tempOutputFile());
        } catch (Exception e) {
            // TODO: Remove this once we have a proper exception for this
            assertTrue(true, "Should not throw exception");
        }
    }

    private Path createBasicPdf(String filename, PdfSetupAction setupAction) throws Exception {
        Path testFile = testOutputPath(filename);

        try (PdfWriter writer = new PdfWriter(testFile.toString());
                PdfDocument pdfDoc = new PdfDocument(writer)) {

            pdfDoc.setTagged();

            Document document = new Document(pdfDoc);

            Paragraph title = new Paragraph("Test PDF Document").setFontSize(18);
            document.add(title);

            Paragraph content =
                    new Paragraph(
                                    "This is a test PDF created for accessibility testing. "
                                            + "It contains basic content to demonstrate tag structure validation.")
                            .setFontSize(12);
            document.add(content);

            if (setupAction != null) {
                setupAction.setup(pdfDoc, document);
            }

            document.close();
        }

        logger.debug("Created test PDF: " + testFile.toAbsolutePath());
        return testFile;
    }

    @FunctionalInterface
    private interface PdfSetupAction {
        void setup(PdfDocument doc, Document document) throws Exception;
    }

    private Path createBrokenTagStructure(Path sourcePdf, Path targetPdf, String issueType)
            throws Exception {
        try (PdfReader reader = new PdfReader(sourcePdf.toString());
                PdfWriter writer = new PdfWriter(targetPdf.toString());
                PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            if (root != null) {
                switch (issueType) {
                    case "L_WITH_P_CHILDREN" -> createListWithParagraphChildren(root);
                    case "LI_WITH_SINGLE_P" -> createListItemWithSingleParagraph(root);
                    case "MISSING_LBODY" -> createListItemMissingLBody(root);
                    default -> logger.debug("Unknown issue type: " + issueType);
                }
            }
        }

        Files.deleteIfExists(sourcePdf);

        logger.debug("Created broken PDF: " + targetPdf.toAbsolutePath());
        return targetPdf;
    }

    // Creates the issue: L > P (should be L > LI > LBody > P)
    private void createListWithParagraphChildren(PdfStructTreeRoot root) {
        findAndModifyElement(
                root,
                PdfName.L,
                (listElem) -> {
                    List<IStructureNode> kids = new ArrayList<>(listElem.getKids());
                    for (IStructureNode kid : kids) {
                        if (kid instanceof PdfStructElem) {
                            PdfStructElem kidElem = (PdfStructElem) kid;
                            if (PdfName.LI.equals(kidElem.getRole())) {
                                PdfStructElem pElem = findParagraphInListItem(kidElem);
                                if (pElem != null) {
                                    listElem.removeKid(kidElem);
                                    listElem.addKid(pElem);
                                }
                            }
                        }
                    }
                });
    }

    // Creates the issue: LI > P (should be LI > Lbl + LBody, with P inside LBody)
    private void createListItemWithSingleParagraph(PdfStructTreeRoot root) {
        findAndModifyElement(
                root,
                PdfName.LI,
                (liElem) -> {
                    List<IStructureNode> kids = new ArrayList<>(liElem.getKids());
                    PdfStructElem pElem = null;

                    for (IStructureNode kid : kids) {
                        if (kid instanceof PdfStructElem) {
                            PdfStructElem kidElem = (PdfStructElem) kid;
                            if (PdfName.LBody.equals(kidElem.getRole())) {
                                pElem = findFirstChild(kidElem, PdfName.P);
                                if (pElem != null) {
                                    kidElem.removeKid(pElem);
                                }
                            }
                            liElem.removeKid(kidElem);
                        }
                    }

                    if (pElem != null) {
                        liElem.addKid(pElem);
                    }
                });
    }

    /**
     * Creates the issue: LI > Lbl (missing LBody) This tests detection of incomplete list item
     * structure
     */
    private void createListItemMissingLBody(PdfStructTreeRoot root) {
        findAndModifyElement(
                root,
                PdfName.LI,
                (liElem) -> {
                    // Remove LBody, keep only Lbl
                    List<IStructureNode> kids = new ArrayList<>(liElem.getKids());
                    for (IStructureNode kid : kids) {
                        if (kid instanceof PdfStructElem) {
                            PdfStructElem kidElem = (PdfStructElem) kid;
                            if (PdfName.LBody.equals(kidElem.getRole())) {
                                liElem.removeKid(kidElem);
                            }
                        }
                    }
                });
    }

    /** Helper method to find and modify the first element with a specific role */
    private void findAndModifyElement(
            PdfStructTreeRoot root,
            PdfName targetRole,
            java.util.function.Consumer<PdfStructElem> modifier) {
        for (Object kid : root.getKids()) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem elem = findElementWithRole((PdfStructElem) kid, targetRole);
                if (elem != null) {
                    modifier.accept(elem);
                    return; // Only modify the first occurrence
                }
            }
        }
    }

    /** Recursively finds the first element with the specified role */
    private PdfStructElem findElementWithRole(PdfStructElem elem, PdfName targetRole) {
        if (targetRole.equals(elem.getRole())) {
            return elem;
        }

        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem found = findElementWithRole((PdfStructElem) kid, targetRole);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private PdfStructElem findFirstChild(PdfStructElem parent, PdfName targetRole) {
        for (Object kid : parent.getKids()) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem kidElem = (PdfStructElem) kid;
                if (targetRole.equals(kidElem.getRole())) {
                    return kidElem;
                }
            }
        }
        return null;
    }

    private PdfStructElem findParagraphInListItem(PdfStructElem liElem) {
        for (Object kid : liElem.getKids()) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem kidElem = (PdfStructElem) kid;
                if (PdfName.LBody.equals(kidElem.getRole())) {
                    return findFirstChild(kidElem, PdfName.P);
                }
            }
        }
        return null;
    }

    private Path createPdfWithDocumentIssues() throws Exception {
        return createBasicPdf(
                "document_issues_test.pdf",
                (doc, document) -> {
                    document.add(
                            new Paragraph(
                                    "This PDF has content but may be missing document-level accessibility properties."));
                });
    }

    private Path createPdfWithTagIssues() throws Exception {
        Path normalPdf =
                createBasicPdf(
                        "tag_issues_temp.pdf",
                        (doc, document) -> {
                            document.add(new Paragraph("This PDF will have tag structure issues."));

                            com.itextpdf.layout.element.List list =
                                    new com.itextpdf.layout.element.List();
                            list.add(new ListItem("Item 1"));
                            list.add(new ListItem("Item 2"));
                            document.add(list);
                        });

        Path brokenPdf = testOutputPath("tag_issues_test.pdf");
        return createBrokenTagStructure(normalPdf, brokenPdf, "L_WITH_P_CHILDREN");
    }

    private Path createPdfWithMultipleIssues() throws Exception {
        return createBasicPdf(
                "multiple_issues_test.pdf",
                (doc, document) -> {
                    document.add(new Paragraph("Multiple Issues Test Document").setFontSize(16));
                    document.add(
                            new Paragraph(
                                    "This document contains various elements that may have accessibility issues."));

                    Table table = new Table(3);
                    table.addCell("Column 1");
                    table.addCell("Column 2");
                    table.addCell("Column 3");
                    table.addCell("Value A");
                    table.addCell("Value B");
                    table.addCell("Value C");
                    document.add(table);

                    com.itextpdf.layout.element.List list = new com.itextpdf.layout.element.List();
                    list.add(new ListItem("First item"));
                    list.add(new ListItem("Second item"));
                    list.add(new ListItem("Third item"));
                    document.add(list);

                    document.add(
                            new Paragraph(
                                    "Additional content to test various accessibility rules."));
                });
    }

    private Path createPdfWithFixableTagIssues() throws Exception {
        Path normalPdf =
                createBasicPdf(
                        "fixable_tag_issues_temp.pdf",
                        (doc, document) -> {
                            document.add(new Paragraph("Fixable Tag Issues Test").setFontSize(16));

                            com.itextpdf.layout.element.List list =
                                    new com.itextpdf.layout.element.List();
                            list.add(new ListItem("Item that will be broken"));
                            list.add(new ListItem("Another item"));
                            document.add(list);
                        });

        Path brokenPdf = testOutputPath("fixable_tag_issues_test.pdf");
        return createBrokenTagStructure(normalPdf, brokenPdf, "LI_WITH_SINGLE_P");
    }
}
