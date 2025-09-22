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
    private final Path outputPath;
    private final PrintStream output;
    private final RuleEngine engine;

    // State variables that persist through processing
    private EncryptionInfo encryptionInfo;
    private ProcessingContext context;

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

    public ProcessingService(Path inputPath, Path outputPath, String password, PrintStream output) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
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

    public ProcessingResult process() throws Exception {
        // File system setup
        setupOutputPath();
        validateInputFile();

        // Analyze encryption
        this.encryptionInfo = analyzeEncryption();

        // Create PDF document for processing
        try (PdfDocument pdfDoc = openForModification()) {
            this.context = new ProcessingContext(pdfDoc, output);

            List<Issue> issues = detectAndReportIssues();
            applyFixesAndReport(issues);
            ProcessingResult result = new ProcessingResult(issues);
            printSummary(result);

            return result;
        }
    }

    private void setupOutputPath() throws Exception {
        Path outputParent = outputPath.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
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

    private PdfDocument openForModification() throws Exception {
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

    private List<Issue> detectAndReportIssues() {
        List<Issue> allIssues = new java.util.ArrayList<>();

        // Phase 1: Rule-based detection
        List<Issue> ruleIssues = engine.detectIssues(context);
        if (!ruleIssues.isEmpty()) {
            output.println();
            output.println("Document issues found: " + ruleIssues.size());
            output.println("────────────────────────────────────────");
            for (Issue i : ruleIssues) {
                output.println(i.message());
            }
            allIssues.addAll(ruleIssues);
        }

        // Phase 2: Tag structure validation
        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            output.println("✗ No accessibility tags found");
        } else {
            output.println();
            output.println("Tag structure validation:");
            output.println("────────────────────────────────────────");

            TagValidator validator = new TagValidator(TagSchema.minimalLists(), output);
            List<Issue> tagIssues = validator.validate(root);

            if (tagIssues.isEmpty()) {
                output.println("✓ No issues found in tag structure");
            } else {
                output.println("Tag issues found: " + tagIssues.size());
                allIssues.addAll(tagIssues);
            }
        }

        return allIssues;
    }

    private void applyFixesAndReport(List<Issue> issues) {
        output.println();
        output.println("Applying automatic fixes:");
        output.println("────────────────────────────────────────");

        engine.applyFixes(context, issues);

        // Report remaining issues
        if (!issues.isEmpty()) {
            List<Issue> remaining = issues.stream().filter(i -> !i.isResolved()).toList();
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
    }

    private void printSummary(ProcessingResult result) {
        output.println();
        output.println("=== REMEDIATION SUMMARY ===");

        int detected = result.getDetectedIssues().size();
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
