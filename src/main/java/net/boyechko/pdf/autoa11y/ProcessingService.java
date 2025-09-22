package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.rules.*;

public class ProcessingService {
    private final Path inputPath;
    private final Path outputPath;
    private final String password;
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
        this.output = output;
        this.engine = new RuleEngine(List.of(
            new LanguageSetRule(),
            new TabOrderRule(),
            new TaggedPdfRule()
        ));
    }

    public ProcessingResult process() {
        try {
            // File system setup
            setupOutputPath();
            validateInputFile();

            // Setup reader properties
            ReaderProperties readerProps = createReaderProperties();

            // Analyze encryption
            this.encryptionInfo = analyzeEncryption(readerProps);

            // Create PDF document for processing
            try (PdfDocument pdfDoc = createPdfDocument(readerProps)) {
                this.context = new ProcessingContext(pdfDoc, output);

                // Detect all issues
                List<Issue> issues = detectAllIssues();
                int totalIssues = issues.size();

                // Apply fixes and report
                int totalChanges = applyFixesAndReport(issues);

                return ProcessingResult.success(totalIssues, totalChanges, 0);
            }

        } catch (Exception e) {
            return handleError(e);
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

    private ReaderProperties createReaderProperties() {
        ReaderProperties readerProps = new ReaderProperties();
        if (password != null) {
            readerProps.setPassword(password.getBytes());
        }
        return readerProps;
    }

    private EncryptionInfo analyzeEncryption(ReaderProperties readerProps) throws Exception {
        try (PdfReader testReader = new PdfReader(inputPath.toString(), readerProps);
             PdfDocument testDoc = new PdfDocument(testReader)) {

            return new EncryptionInfo(
                testReader.getPermissions(),
                testReader.getCryptoMode(),
                testReader.isEncrypted()
            );
        }
    }

    private PdfDocument createPdfDocument(ReaderProperties readerProps) throws Exception {
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

    private List<Issue> detectAllIssues() {
        List<Issue> allIssues = new java.util.ArrayList<>();

        // Phase 1: Rule-based detection
        List<Issue> ruleIssues = engine.detectAll(context);
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

    private int applyFixesAndReport(List<Issue> issues) {
        output.println();
        output.println("Applying automatic fixes:");
        output.println("────────────────────────────────────────");

        int changesApplied = (int) engine.applyFixes(context, issues).size();

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

        return changesApplied;
    }

    private ProcessingResult handleError(Exception e) {
        if (password == null && e.getMessage().contains("Bad user password")) {
            return ProcessingResult.error("The PDF is password-protected. Please provide a password.");
        } else {
            return ProcessingResult.error("Processing error: " + e.getMessage());
        }
    }

}
