package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.boyechko.pdf.autoa11y.rules.*;

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
    private final int DEFAULT_PERMISSIONS = EncryptionConstants.ALLOW_PRINTING
            | EncryptionConstants.ALLOW_FILL_IN
            | EncryptionConstants.ALLOW_MODIFY_ANNOTATIONS
            | EncryptionConstants.ALLOW_SCREENREADERS;
    private final int DEFAULT_CRYPTO_MODE = EncryptionConstants.ENCRYPTION_AES_256
            | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;

    public ProcessingService(Path inputPath, String password, PrintStream output) {
        this.inputPath = inputPath;
        this.password = password;
        this.readerProps = new ReaderProperties();
        if (password != null) {
            this.readerProps.setPassword(password.getBytes());
        }
        this.output = output;
        this.engine = new RuleEngine(List.of(
            new LanguageSetRule(),
            new TabOrderRule(),
            new StructureTreeRule(),
            new TaggedPdfRule()
        ));
    }

    public record ProcessingResult(IssueList issues, Path tempOutputFile) {}

    public ProcessingResult process() throws Exception {
        validateInputFile();
        Path tempOutputFile = Files.createTempFile("pdf_autoa11y_", ".pdf");

        this.encryptionInfo = analyzeEncryption();

        try (PdfDocument pdfDoc = openForModification(tempOutputFile)) {
            this.context = new DocumentContext(pdfDoc);

            // Phase 1: Initial detection
            IssueList issues = detectAndReportIssues();

            // Phase 2: Apply fixes
            IssueList appliedFixes = applyFixesAndReport(issues);

            // Phase 3: Re-validate and report remaining issues
            IssueList remainingIssues = detectAndReportIssues();
            reportRemainingIssues(remainingIssues);

            printSummary(issues, appliedFixes, remainingIssues);
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
                testReader.isEncrypted()
            );
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
                encryptionInfo.cryptoMode()
            );
        } else if (password != null) {
            // Set default encryption if input not encrypted but password provided
            writerProps.setStandardEncryption(
                null,
                password.getBytes(),
                DEFAULT_PERMISSIONS,
                DEFAULT_CRYPTO_MODE
            );
        }

        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    private IssueList detectAndReportIssues() {
        IssueList allIssues = engine.detectIssues(context);

        // Phase 1: Tag structure validation
        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            output.println("✗ No accessibility tags found");
        } else {
            output.println();
            output.println("Tag structure validation:");
            output.println("────────────────────────────────────────");

            TagSchema schema = TagSchema.loadDefault();
            TagValidator validator = new TagValidator(schema, output);
            List<Issue> tagIssues = validator.validate(root);

            if (tagIssues.isEmpty()) {
                output.println("✓ No issues found in tag structure");
            } else {
                output.println("Tag issues found: " + tagIssues.size());
                allIssues.addAll(tagIssues);
            }
        }

        // Phase 2: Rule-based detection
        output.println();
        output.println("Checking document compliance:");
        output.println("────────────────────────────────────────");

        // Report the results by checking each rule individually for better output
        for (Rule rule : engine.getRules()) {
            IssueList ruleIssues = rule.findIssues(context);
            if (ruleIssues.isEmpty()) {
                output.println("✓ " + rule.name() + " - compliant");
            } else {
                for (Issue issue : ruleIssues) {
                    output.println(issue.message());
                }
            }
        }

        return allIssues;
    }

    private IssueList applyFixesAndReport(IssueList issues) {
        if (issues.isEmpty()) {
            return new IssueList(); // No issues to fix
        }

        output.println();
        output.println("Applying automatic fixes:");
        output.println("────────────────────────────────────────");

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

    private void printSummary(IssueList originalIssues, IssueList appliedFixes, IssueList remainingIssues) {
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
