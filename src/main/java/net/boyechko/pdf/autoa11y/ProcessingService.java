package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.rules.*;

public class ProcessingService {

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

    private static void setupOutputPath(Path outputPath) throws Exception {
        Path outputParent = outputPath.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }
    }

    private static void validateInputFile(Path inputPath) throws Exception {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("File not found: " + inputPath);
        }
    }

    private static EncryptionInfo analyzeEncryption(Path inputPath, ReaderProperties readerProps) throws Exception {
        try (PdfReader testReader = new PdfReader(inputPath.toString(), readerProps);
             PdfDocument testDoc = new PdfDocument(testReader)) {

            return new EncryptionInfo(
                testReader.getPermissions(),
                testReader.getCryptoMode(),
                testReader.isEncrypted()
            );
        }
    }

    private static PdfDocument createPdfDocument(Path inputPath, Path outputPath, String password, ReaderProperties readerProps, EncryptionInfo encInfo) throws Exception {
        PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
        WriterProperties writerProps = new WriterProperties();

        if (encInfo.isEncrypted() && password != null) {
            writerProps.setStandardEncryption(
                null,
                password.getBytes(),
                encInfo.permissions(),
                encInfo.cryptoMode()
            );
        }

        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    private static List<Issue> detectAllIssues(PdfDocument pdfDoc, ProcessingContext ctx, PrintStream output) {
        List<Issue> allIssues = new java.util.ArrayList<>();

        // Phase 1: Rule-based detection
        RuleEngine engine = new RuleEngine(java.util.List.of(
            new LanguageSetRule(),
            new TabOrderRule(),
            new TaggedPdfRule()
        ));

        List<Issue> ruleIssues = engine.detectAll(ctx);
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
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
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

    private static int applyFixesAndReport(List<Issue> issues, ProcessingContext ctx, PrintStream output) {
        RuleEngine engine = new RuleEngine(java.util.List.of(
            new LanguageSetRule(),
            new TabOrderRule(),
            new TaggedPdfRule()
        ));

        output.println();
        output.println("Applying automatic fixes:");
        output.println("────────────────────────────────────────");

        int changesApplied = (int) engine.applyFixes(ctx, issues).size();

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

    public static ProcessingResult processPdf(Path inputPath, Path outputPath, String password, PrintStream output) {
        try {
            // File system setup
            setupOutputPath(outputPath);
            validateInputFile(inputPath);

            // Setup reader properties
            ReaderProperties readerProps = new ReaderProperties();
            if (password != null) {
                readerProps.setPassword(password.getBytes());
            }

            // Analyze encryption
            EncryptionInfo encInfo = analyzeEncryption(inputPath, readerProps);

            // Create PDF document for processing
            try (PdfDocument pdfDoc = createPdfDocument(inputPath, outputPath, password, readerProps, encInfo)) {
                ProcessingContext ctx = new ProcessingContext(pdfDoc, output);

                // Detect all issues
                List<Issue> issues = detectAllIssues(pdfDoc, ctx, output);
                int totalIssues = issues.size();

                // Apply fixes and report
                int totalChanges = applyFixesAndReport(issues, ctx, output);

                return ProcessingResult.success(totalIssues, totalChanges, 0);
            }

        } catch (Exception e) {
            if (password == null && e.getMessage().contains("Bad user password")) {
                return ProcessingResult.error("The PDF is password-protected. Please provide a password.");
            } else {
                return ProcessingResult.error("Processing error: " + e.getMessage());
            }
        }
    }
}
