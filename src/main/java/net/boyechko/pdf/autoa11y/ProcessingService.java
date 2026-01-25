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
package net.boyechko.pdf.autoa11y;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import net.boyechko.pdf.autoa11y.rules.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    private final Path inputPath;
    private final String password;
    private final ReaderProperties readerProps;
    private final PrintStream output;
    private final RuleEngine engine;
    private final VerbosityLevel verbosity;
    private final OutputFormatter formatter;

    // State variables that persist through processing
    private EncryptionInfo encryptionInfo;
    private DocumentContext context;

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

    private final int DEFAULT_PERMISSIONS =
            EncryptionConstants.ALLOW_PRINTING
                    | EncryptionConstants.ALLOW_FILL_IN
                    | EncryptionConstants.ALLOW_MODIFY_ANNOTATIONS
                    | EncryptionConstants.ALLOW_SCREENREADERS;
    private final int DEFAULT_CRYPTO_MODE =
            EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;

    public ProcessingService(
            Path inputPath, String password, PrintStream output, VerbosityLevel verbosity) {
        this.inputPath = inputPath;
        this.password = password;
        this.readerProps = new ReaderProperties();
        if (password != null) {
            this.readerProps.setPassword(password.getBytes());
        }
        this.output = output;
        this.verbosity = verbosity;
        this.formatter = new OutputFormatter(output, verbosity);
        this.engine =
                new RuleEngine(
                        List.of(
                                new LanguageSetRule(),
                                new TabOrderRule(),
                                new StructureTreeRule(),
                                new TaggedPdfRule()));
    }

    public ProcessingService(Path inputPath, String password, PrintStream output) {
        this(inputPath, password, output, VerbosityLevel.NORMAL);
    }

    public record ProcessingResult(
            IssueList originalTagIssues,
            IssueList appliedTagFixes,
            IssueList remainingTagIssues,
            IssueList documentLevelIssues,
            IssueList appliedDocumentFixes,
            IssueList totalRemainingIssues,
            Path tempOutputFile) {

        public int totalIssuesDetected() {
            return originalTagIssues.size() + documentLevelIssues.size();
        }

        public int totalIssuesResolved() {
            return appliedTagFixes.size() + appliedDocumentFixes.size();
        }

        public int totalIssuesRemaining() {
            return totalRemainingIssues.size();
        }

        public boolean hasTagIssues() {
            return !originalTagIssues.isEmpty();
        }

        public boolean hasDocumentIssues() {
            return !documentLevelIssues.isEmpty();
        }
    }

    public class NoTagsException extends Exception {
        public NoTagsException(String message) {
            super(message);
        }
    }

    public ProcessingResult process() throws Exception {
        validateInputFile();
        Path tempOutputFile = Files.createTempFile("pdf_autoa11y_", ".pdf");

        this.encryptionInfo = analyzeEncryption();

        try (PdfDocument pdfDoc = openForModification(tempOutputFile)) {
            this.context = new DocumentContext(pdfDoc);

            analyzeAndRemediate();

            ProcessingResult result = this.context.getProcessingResult();
            return new ProcessingResult(
                    result.originalTagIssues(),
                    result.appliedTagFixes(),
                    result.remainingTagIssues(),
                    result.documentLevelIssues(),
                    result.appliedDocumentFixes(),
                    result.totalRemainingIssues(),
                    tempOutputFile);
        } catch (Exception e) {
            Files.deleteIfExists(tempOutputFile);
            throw e;
        }
    }

    private void validateInputFile() throws Exception {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("File not found: " + inputPath);
        }
    }

    private EncryptionInfo analyzeEncryption() throws Exception {
        try (PdfReader testReader = new PdfReader(inputPath.toString(), readerProps);
                PdfDocument testDoc = new PdfDocument(testReader)) {
            logger.debug("PDF Encryption Analysis:");
            logger.debug("  Encrypted: " + testReader.isEncrypted());
            logger.debug("  Permissions: " + testReader.getPermissions());
            logger.debug("  Crypto Mode: " + testReader.getCryptoMode());
            return new EncryptionInfo(
                    testReader.getPermissions(),
                    testReader.getCryptoMode(),
                    testReader.isEncrypted());
        }
    }

    private PdfDocument openForModification(Path outputPath) throws Exception {
        PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
        WriterProperties writerProps = new WriterProperties();

        if (encryptionInfo.isEncrypted() && password != null) {
            writerProps.setStandardEncryption(
                    null,
                    password.getBytes(),
                    encryptionInfo.permissions(),
                    encryptionInfo.cryptoMode());
        } else if (password != null) {
            // Set default encryption if input not encrypted but password provided
            writerProps.setStandardEncryption(
                    null, password.getBytes(), DEFAULT_PERMISSIONS, DEFAULT_CRYPTO_MODE);
        }

        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    private IssueList analyzeAndRemediate() throws Exception {
        formatter.printPhase(1, 4, "Validating tag structure");
        IssueList originalTagIssues = detectAndReportTagIssues();

        formatter.printPhase(2, 4, "Applying automatic fixes");
        IssueList appliedTagFixes = applyFixesAndReport(originalTagIssues);

        IssueList remainingTagIssues;
        if (!appliedTagFixes.isEmpty()) {
            formatter.printPhase(3, 4, "Re-validating tag structure");
            remainingTagIssues = detectAndReportTagIssues();
        } else {
            remainingTagIssues = originalTagIssues;
        }

        formatter.printPhase(4, 4, "Checking document-level compliance");
        IssueList documentLevelIssues = detectAndReportRuleIssues();
        IssueList appliedDocumentFixes = applyFixesAndReport(documentLevelIssues);

        IssueList totalRemainingIssues = new IssueList();
        totalRemainingIssues.addAll(remainingTagIssues);
        totalRemainingIssues.addAll(documentLevelIssues.getRemainingIssues());

        IssueList totalAppliedFixes = new IssueList();
        totalAppliedFixes.addAll(appliedTagFixes);
        totalAppliedFixes.addAll(appliedDocumentFixes);

        printSummary(originalTagIssues, totalAppliedFixes, totalRemainingIssues);

        this.context.setProcessingResult(
                new ProcessingResult(
                        originalTagIssues,
                        appliedTagFixes,
                        remainingTagIssues,
                        documentLevelIssues,
                        appliedDocumentFixes,
                        totalRemainingIssues,
                        null));

        return totalAppliedFixes;
    }

    private IssueList detectAndReportTagIssues() throws NoTagsException {
        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            throw new NoTagsException("No accessibility tags found");
        }

        TagSchema schema = TagSchema.loadDefault();
        // Only show tag structure in VERBOSE mode or higher
        PrintStream tagOutput = verbosity.isAtLeast(VerbosityLevel.VERBOSE) ? output : null;
        TagValidator validator = new TagValidator(schema, tagOutput);
        List<Issue> tagIssues = validator.validate(root);

        IssueList issueList = new IssueList();
        issueList.addAll(tagIssues);

        if (tagIssues.isEmpty()) {
            formatter.printSuccess("No issues found");
        } else {
            formatter.printWarning("Found " + tagIssues.size() + " issue(s)");
        }

        return issueList;
    }

    private IssueList detectAndReportRuleIssues() {
        IssueList allRuleIssues = new IssueList();

        // Run each rule individually for better output control
        for (Rule rule : engine.getRules()) {
            IssueList ruleIssues = rule.findIssues(context);
            allRuleIssues.addAll(ruleIssues);

            if (formatter.shouldShow(VerbosityLevel.NORMAL)) {
                if (ruleIssues.isEmpty()) {
                    formatter.printSuccess(rule.name());
                } else {
                    for (Issue issue : ruleIssues) {
                        formatter.printWarning(issue.message());
                    }
                }
            }
        }

        return allRuleIssues;
    }

    private IssueList applyFixesAndReport(IssueList issues) {
        if (issues.isEmpty()) {
            formatter.printSuccess("No fixes needed");
            return new IssueList(); // No issues to fix
        }

        IssueList appliedFixes = engine.applyFixes(context, issues);

        // Report successful fixes
        if (formatter.shouldShow(VerbosityLevel.NORMAL)) {
            for (Issue issue : appliedFixes) {
                if (issue.isResolved() && issue.fix() != null) {
                    formatter.printSuccess(issue.resolutionNote());
                }
            }
        }

        return appliedFixes;
    }

    private void printSummary(
            IssueList originalIssues, IssueList appliedFixes, IssueList remainingIssues) {
        int detected = originalIssues.size();
        int remaining = remainingIssues.size();
        int resolved = detected - remaining;

        formatter.printSummary(detected, resolved, remaining);
    }
}
