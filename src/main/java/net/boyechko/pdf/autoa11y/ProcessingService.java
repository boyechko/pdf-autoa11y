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

    public ProcessingService(Path inputPath, String password, PrintStream output) {
        this.inputPath = inputPath;
        this.password = password;
        this.readerProps = new ReaderProperties();
        if (password != null) {
            this.readerProps.setPassword(password.getBytes());
        }
        this.output = output;
        this.engine =
                new RuleEngine(
                        List.of(
                                new LanguageSetRule(),
                                new TabOrderRule(),
                                new StructureTreeRule(),
                                new TaggedPdfRule()));
    }

    public record ProcessingResult(IssueList issues, Path tempOutputFile) {}

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

            // Delegate to a business logic method
            IssueList remainingIssues = analyzeAndRemediate();

            return new ProcessingResult(remainingIssues, tempOutputFile);
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
        // Phase 1: Initial detection of tag issues
        output.println();
        output.println("Validating tag structure:");
        output.println("────────────────────────────────────────");
        IssueList tagIssues = detectAndReportTagIssues();

        // Phase 2: Apply fixes
        output.println();
        output.println("Applying automatic fixes:");
        output.println("────────────────────────────────────────");
        IssueList appliedTagFixes = applyFixesAndReport(tagIssues);

        IssueList remainingIssues;
        if (!appliedTagFixes.isEmpty()) {
            // Phase 3: Re-validate and report remaining issues
            output.println();
            output.println("Re-validating tag structure:");
            output.println("────────────────────────────────────────");
            remainingIssues = detectAndReportTagIssues();
        } else {
            remainingIssues = tagIssues; // No fixes applied, so remaining are the same as original
        }

        // Phase 4: Check for document-level issues
        output.println();
        output.println("Checking for document-level issues:");
        output.println("────────────────────────────────────────");
        IssueList ruleIssues = detectAndReportRuleIssues();
        remainingIssues.addAll(ruleIssues);
        IssueList appliedRuleFixes = applyFixesAndReport(remainingIssues);

        IssueList totalAppliedFixes = new IssueList();
        totalAppliedFixes.addAll(appliedTagFixes);
        totalAppliedFixes.addAll(appliedRuleFixes);

        printSummary(tagIssues, totalAppliedFixes, remainingIssues);
        return totalAppliedFixes;
    }

    private IssueList detectAndReportTagIssues() throws NoTagsException {
        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            throw new NoTagsException("No accessibility tags found");
        }

        TagSchema schema = TagSchema.loadDefault();
        TagValidator validator = new TagValidator(schema, output);
        List<Issue> tagIssues = validator.validate(root);

        IssueList issueList = new IssueList();
        issueList.addAll(tagIssues);

        if (tagIssues.isEmpty()) {
            output.println("✓ No issues found in tag structure");
        } else {
            output.println("✗ Tag issues found: " + tagIssues.size());
        }

        return issueList;
    }

    private IssueList detectAndReportRuleIssues() {
        IssueList allRuleIssues = new IssueList();

        // Run each rule individually for better output control
        for (Rule rule : engine.getRules()) {
            IssueList ruleIssues = rule.findIssues(context);
            allRuleIssues.addAll(ruleIssues);

            if (ruleIssues.isEmpty()) {
                output.println("✓ " + rule.name() + " - compliant");
            } else {
                for (Issue issue : ruleIssues) {
                    output.println(issue.message());
                }
            }
        }

        return allRuleIssues;
    }

    private IssueList applyFixesAndReport(IssueList issues) {
        if (issues.isEmpty()) {
            output.println("✓ No fixes needed");
            return new IssueList(); // No issues to fix
        }

        IssueList appliedFixes = engine.applyFixes(context, issues);

        // Report successful fixes
        for (Issue issue : appliedFixes) {
            if (issue.isResolved() && issue.fix() != null) {
                output.println("✓ " + issue.resolutionNote());
            }
        }

        return appliedFixes;
    }

    private void reportRemainingIssues(IssueList issues) {
        IssueList remaining = issues.getRemainingIssues();
        if (!remaining.isEmpty()) {
            output.println();
            output.println("Remaining issues after fixes:");
            output.println("────────────────────────────────────────");
            for (Issue i : remaining) {
                output.println("✗ " + i.message() + ": " + i.where());
            }
        }
    }

    private void printSummary(
            IssueList originalIssues, IssueList appliedFixes, IssueList remainingIssues) {
        output.println();
        output.println("=== REMEDIATION SUMMARY ===");

        int detected = originalIssues.size();
        int remaining = remainingIssues.size();
        int resolved = detected - remaining;

        if (detected == 0 && resolved == 0) {
            output.println("✓ Document structure is already compliant");
        } else {
            output.println("✗ Issues found: " + detected);
            output.println("✓ Resolved: " + resolved);
            if (remaining > 0) {
                output.println("⚠ Manual review needed for: " + remaining);
            }
        }
    }
}
