package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.rules.*;

public class ProcessingService {
    private PrintStream output = System.out;

    public ProcessingResult processPdf(ProcessingRequest request) {
        this.output = request.getOutputStream();

        try {
            // Only create directories if the output path has a parent
            Path outputParent = request.getOutputPath().getParent();
            if (outputParent != null) {
                Files.createDirectories(outputParent);
            }

            // Check if input file exists
            if (!Files.exists(request.getInputPath())) {
                return ProcessingResult.error("File not found: " + request.getInputPath());
            }

            // Setup reader properties
            ReaderProperties readerProps = new ReaderProperties();
            if (request.getPassword() != null) {
                readerProps.setPassword(request.getPassword().getBytes());
            }

            // Open for reading to check encryption properties
            PdfReader testReader = new PdfReader(request.getInputPath().toString(), readerProps);
            PdfDocument testDoc = new PdfDocument(testReader);

            int permissions = testReader.getPermissions();
            int cryptoMode = testReader.getCryptoMode();
            boolean isEncrypted = testReader.isEncrypted();

            testDoc.close();

            // Now open for processing
            PdfReader pdfReader = new PdfReader(request.getInputPath().toString(), readerProps);
            WriterProperties writerProps = new WriterProperties();

            if (isEncrypted && request.getPassword() != null) {
                writerProps.setStandardEncryption(
                    null,
                    request.getPassword().getBytes(),
                    permissions,
                    cryptoMode
                );
            }

            writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
            PdfWriter pdfWriter = new PdfWriter(request.getOutputPath().toString(), writerProps);

            try (PdfDocument pdfDoc = new PdfDocument(pdfReader, pdfWriter)) {
                int totalChanges = 0;
                int totalWarnings = 0;
                int totalIssues = 0;

                RuleEngine engine = new RuleEngine(java.util.List.of(
                    new LanguageSetRule(),
                    new TabOrderRule(),
                    new TaggedPdfRule()
                ));
                ProcessingContext ctx = new ProcessingContext(pdfDoc, output);

                // Phase 1: Detect issues
                List<Issue> issues = engine.detectAll(ctx);
                if (!issues.isEmpty()) {
                    totalIssues += issues.size();
                    output.println("Issues detected: ");
                    output.println("────────────────────────────────────────");
                    for (Issue i : issues) {
                        output.println(i.message());
                    }
                }

                // Step 0: Validate the tag structure
                PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
                if (root == null || root.getKids() == null) {
                    output.println("✗ No accessibility tags found");
                } else {
                    output.println();
                    output.println("Tag structure validation:");
                    output.println("────────────────────────────────────────");

                    TagValidator validator = new TagValidator(TagSchema.minimalLists(), output);
                    List<Issue> tagIssues = validator.validate(root);
                    totalIssues += tagIssues.size();

                    if (issues.isEmpty()) {
                        output.println("✓ No issues found in tag structure");
                    } else {
                        output.println("✗ Tag issues found: " + tagIssues.size());
                    }
                }

                // Step 1: Tag structure normalization
                output.println();
                output.println("Tag structure analysis and fixes:");
                output.println("────────────────────────────────────────");

                TagNormalizer normalizer = new TagNormalizer(pdfDoc, output);
                normalizer.processAndDisplayChanges();

                totalChanges += normalizer.getChangeCount();
                totalWarnings += normalizer.getWarningCount();

                // Step 2: Apply rule-based fixes
                output.println();
                output.println("Applying rule-based fixes:");
                output.println("────────────────────────────────────────");
                engine.applyFixes(ctx, issues);

                return ProcessingResult.success(totalIssues, totalChanges, totalWarnings);
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
