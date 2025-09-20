package net.boyechko.pdf.autoa11y;

import java.nio.file.*;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.rules.*;

public class ProcessingService {

    public ProcessingResult processPdf(ProcessingRequest request) {
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
                ProcessingContext ctx = new ProcessingContext(pdfDoc, request.getOutputStream());

                // Phase 1: Detect issues
                List<Issue> issues = engine.detectAll(ctx);
                if (!issues.isEmpty()) {
                    totalIssues += issues.size();
                    request.getOutputStream().println("Issues detected: ");
                    request.getOutputStream().println("────────────────────────────────────────");
                    for (Issue i : issues) {
                        request.getOutputStream().println(i.message());
                    }
                }

                // Step 0: Validate the tag structure
                TagValidator validator = new TagValidator(TagSchema.minimalLists(), request.getOutputStream());
                List<Issue> tagIssues = validator.validate(pdfDoc.getStructTreeRoot());
                totalIssues += tagIssues.size();

               request.getOutputStream().println();

                // Step 1: Tag structure normalization
                request.getOutputStream().println("Tag structure analysis and fixes:");
                request.getOutputStream().println("────────────────────────────────────────");

                TagNormalizer normalizer = new TagNormalizer(pdfDoc, request.getOutputStream());
                normalizer.processAndDisplayChanges();

                totalChanges += normalizer.getChangeCount();
                totalWarnings += normalizer.getWarningCount();

                // Step 2: Apply rule-based fixes
                request.getOutputStream().println();
                request.getOutputStream().println("Applying rule-based fixes:");
                request.getOutputStream().println("────────────────────────────────────────");
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
