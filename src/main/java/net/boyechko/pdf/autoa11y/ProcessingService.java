package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.rules.*;

public class ProcessingService {
    private PrintStream output = System.out;

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

    private void setupOutputPath(ProcessingRequest request) throws Exception {
        Path outputParent = request.getOutputPath().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }
    }

    private void validateInputFile(ProcessingRequest request) throws Exception {
        if (!Files.exists(request.getInputPath())) {
            throw new IllegalArgumentException("File not found: " + request.getInputPath());
        }
    }

    private EncryptionInfo analyzeEncryption(ProcessingRequest request, ReaderProperties readerProps) throws Exception {
        try (PdfReader testReader = new PdfReader(request.getInputPath().toString(), readerProps);
             PdfDocument testDoc = new PdfDocument(testReader)) {

            return new EncryptionInfo(
                testReader.getPermissions(),
                testReader.getCryptoMode(),
                testReader.isEncrypted()
            );
        }
    }

    private PdfDocument createPdfDocument(ProcessingRequest request, ReaderProperties readerProps, EncryptionInfo encInfo) throws Exception {
        PdfReader pdfReader = new PdfReader(request.getInputPath().toString(), readerProps);
        WriterProperties writerProps = new WriterProperties();

        if (encInfo.isEncrypted() && request.getPassword() != null) {
            writerProps.setStandardEncryption(
                null,
                request.getPassword().getBytes(),
                encInfo.permissions(),
                encInfo.cryptoMode()
            );
        }

        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(request.getOutputPath().toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    private List<Issue> detectAllIssues(PdfDocument pdfDoc, ProcessingContext ctx) {
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

    private int applyFixesAndReport(List<Issue> issues, ProcessingContext ctx) {
        RuleEngine engine = new RuleEngine(java.util.List.of(
            new LanguageSetRule(),
            new TabOrderRule(),
            new TaggedPdfRule()
        ));

        // Report remaining issues
        if (!issues.isEmpty()) {
            output.println();
            output.println("Applying automatic fixes:");
            output.println("────────────────────────────────────────");

            int changesApplied = (int) engine.applyFixes(ctx, issues).size();

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
            return changesApplied;
        }
        return 0;
    }

    public ProcessingResult processPdf(ProcessingRequest request) {
        this.output = request.getOutputStream();

        try {
            // File system setup
            setupOutputPath(request);
            validateInputFile(request);

            // Setup reader properties
            ReaderProperties readerProps = new ReaderProperties();
            if (request.getPassword() != null) {
                readerProps.setPassword(request.getPassword().getBytes());
            }

            // Analyze encryption
            EncryptionInfo encInfo = analyzeEncryption(request, readerProps);

            // Create PDF document for processing
            try (PdfDocument pdfDoc = createPdfDocument(request, readerProps, encInfo)) {
                ProcessingContext ctx = new ProcessingContext(pdfDoc, output);

                // Detect all issues
                List<Issue> issues = detectAllIssues(pdfDoc, ctx);
                int totalIssues = issues.size();

                // Apply fixes and report
                int totalChanges = applyFixesAndReport(issues, ctx);

                return ProcessingResult.success(totalIssues, totalChanges, 0);
            }

        } catch (Exception e) {
            if (request.getPassword() == null && e.getMessage().contains("Bad user password")) {
                return ProcessingResult.error("The PDF is password-protected. Please provide a password.");
            } else {
                return ProcessingResult.error("Processing error: " + e.getMessage());
            }
        }
    }
}
