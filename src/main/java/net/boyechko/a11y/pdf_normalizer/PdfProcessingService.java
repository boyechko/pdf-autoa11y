package net.boyechko.a11y.pdf_normalizer;

import com.itextpdf.kernel.pdf.*;

import java.io.*;
import java.nio.file.*;

public class PdfProcessingService {

    public static class ProcessingRequest {
        private final String inputPath;
        private final String outputPath;
        private final String password;
        private final PrintStream outputStream;

        public ProcessingRequest(String inputPath, String outputPath, String password, PrintStream outputStream) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.password = password;
            this.outputStream = outputStream;
        }

        // Getters
        public String getInputPath() { return inputPath; }
        public String getOutputPath() { return outputPath; }
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
            // Ensure output directory exists
            Files.createDirectories(Paths.get(request.getOutputPath()).getParent());

            // Check if input file exists
            Path srcPath = Paths.get(request.getInputPath());
            if (!Files.exists(srcPath)) {
                return ProcessingResult.error("File not found: " + request.getInputPath());
            }

            // Setup reader properties
            ReaderProperties readerProps = new ReaderProperties();
            if (request.getPassword() != null) {
                readerProps.setPassword(request.getPassword().getBytes());
            }

            // Open for reading to check encryption properties
            PdfReader testReader = new PdfReader(request.getInputPath(), readerProps);
            PdfDocument testDoc = new PdfDocument(testReader);

            int permissions = testReader.getPermissions();
            int cryptoMode = testReader.getCryptoMode();
            boolean isEncrypted = testReader.isEncrypted();

            testDoc.close();

            // Now open for processing
            PdfReader pdfReader = new PdfReader(request.getInputPath(), readerProps);
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
            PdfWriter pdfWriter = new PdfWriter(request.getOutputPath(), writerProps);

            try (PdfDocument pdfDoc = new PdfDocument(pdfReader, pdfWriter)) {
                int totalChanges = 0;
                int totalWarnings = 0;

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
