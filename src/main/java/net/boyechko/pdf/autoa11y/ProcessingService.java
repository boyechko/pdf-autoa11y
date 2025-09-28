package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.rules.*;

public class ProcessingService {
    private final Path inputPath;
    private final String password;
    private final ReaderProperties readerProps;
    private final PrintStream output;
    private final RuleEngine engine;

    // State variables that persist through processing
    private EncryptionInfo encryptionInfo;
    private DocumentContext context;

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

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

            IssueList issues = detectAndReportIssues();
            applyFixesAndReport(issues);
            printSummary(issues);
            return new ProcessingResult(issues, tempOutputFile);
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

            TagValidator validator = new TagValidator(TagSchema.minimal(), output);
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

    private void applyFixesAndReport(IssueList issues) {
        if (issues.isEmpty()) {
            return; // No issues to fix
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

        // Report remaining issues
        IssueList remaining = issues.getRemainingIssues();
        if (!remaining.isEmpty()) {
            output.println();
            output.println("Remaining issues after fixes:");
            output.println("────────────────────────────────────────");
            for (Issue i : remaining) {
                String where = (i.where() != null) ? (" at " + i.where().path()) : "";
                output.println("✗ " + i.message() + where);
            }
        }
    }

    private void printSummary(IssueList result) {
        output.println();
        output.println("=== REMEDIATION SUMMARY ===");

        int detected = result.size();
        int resolved = result.getResolvedIssues().size();
        int remaining = (detected - resolved);

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
