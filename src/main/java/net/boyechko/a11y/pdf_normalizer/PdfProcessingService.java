package net.boyechko.a11y.pdf_normalizer;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import com.itextpdf.kernel.pdf.*;

public class PdfProcessingService {

    public static class ProcessingRequest {
        private final Path inputPath;
        private final Path outputPath;
        private final String password;
        private final PrintStream outputStream;

        public ProcessingRequest(Path inputPath, Path outputPath, String password, PrintStream outputStream) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.password = password;
            this.outputStream = outputStream;
        }

        // Getters
        public Path getInputPath() { return inputPath; }
        public Path getOutputPath() { return outputPath; }
        public String getPassword() { return password; }
        public PrintStream getOutputStream() { return outputStream; }
    }

    public static class ProcessingResult {
        private final boolean success;
        private final int changeCount;
        private final int warningCount;
        private final String errorMessage;

        private ProcessingResult(boolean success, int changeCount, int warningCount, String errorMessage) {
            this.success = success;
            this.changeCount = changeCount;
            this.warningCount = warningCount;
            this.errorMessage = errorMessage;
        }

        public static ProcessingResult success(int changeCount, int warningCount) {
            return new ProcessingResult(true, changeCount, warningCount, null);
        }

        public static ProcessingResult error(String errorMessage) {
            return new ProcessingResult(false, 0, 0, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public int getChangeCount() { return changeCount; }
        public int getWarningCount() { return warningCount; }
        public String getErrorMessage() { return errorMessage; }
    }

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

                // Step 0: Validate the tag structure
                request.getOutputStream().println("Validating existing tag structure:");
                request.getOutputStream().println("────────────────────────────────────────");
                PdfTagValidator validator = new PdfTagValidator(TagSchema.minimalLists());
                List<Issue> issues = validator.validate(pdfDoc.getStructTreeRoot());
                for (Issue issue : issues) {
                    request.getOutputStream().println("Issue: " + issue.nodePath() + " " + issue.message());
                }
                if (issues.isEmpty()) {
                    request.getOutputStream().println("✓ No issues found in tag structure");
                } else {
                    request.getOutputStream().println("Total issues found: " + issues.size());
                }
                request.getOutputStream().println();

                // Step 1: Tag structure normalization
                request.getOutputStream().println("Tag structure analysis and fixes:");
                request.getOutputStream().println("────────────────────────────────────────");

                PdfTagNormalizer normalizer = new PdfTagNormalizer(pdfDoc, request.getOutputStream());
                normalizer.processAndDisplayChanges();

                totalChanges += normalizer.getChangeCount();
                totalWarnings += normalizer.getWarningCount();

                // Step 2: PDF/UA-1 compliance (already set in writer properties)
                request.getOutputStream().println("✓ Set PDF/UA-1 compliance flag");

                // Step 3: Tab order
                int pageCount = pdfDoc.getNumberOfPages();
                for (int i = 1; i <= pageCount; i++) {
                    pdfDoc.getPage(i).setTabOrder(PdfName.S);
                }
                request.getOutputStream().println("✓ Set tab order to structure order for all " + pageCount + " pages");

                return ProcessingResult.success(totalChanges, totalWarnings);
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
