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
                List<Issue> issues = new java.util.ArrayList<>(engine.detectAll(ctx));
                if (!issues.isEmpty()) {
                    totalIssues += issues.size();
                    output.println();
                    output.println("Issues detected: ");
                    output.println("────────────────────────────────────────");
                    for (Issue i : issues) {
                        output.println(i.message());
                    }
                }

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

                    if (tagIssues.isEmpty()) {
                        output.println("✓ No issues found in tag structure");
                    } else {
                        output.println("✗ Tag issues found: " + tagIssues.size());
                    }

                    // Add tag issues to the main issues list for fixing
                    issues.addAll(tagIssues);
                }

                // Step 2: Apply rule-based and tag structure fixes
                output.println();
                output.println("Applying automatic fixes:");
                output.println("────────────────────────────────────────");
                totalChanges += (int) engine.applyFixes(ctx, issues).size();

                // Step 3: List remaining issues
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
                    } else {
                        output.println("✓ All detected issues have been resolved.");
                    }
                }

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
