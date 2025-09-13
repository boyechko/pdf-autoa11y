package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;

import net.boyechko.pdf.preprocess.fixes.PdfUaComplianceFix;
import net.boyechko.pdf.preprocess.fixes.TabOrderFix;
import net.boyechko.pdf.preprocess.fixes.TagNormalizationFix;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PdfProcessingService {

    public static class ProcessingRequest {
        private final String inputPath;
        private final String outputPath;
        private final String password;
        private final PrintStream outputStream;
        private final List<PdfAccessibilityFix> enabledSteps;

        public ProcessingRequest(String inputPath, String outputPath, String password, PrintStream outputStream, List<PdfAccessibilityFix> enabledSteps) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.password = password;
            this.outputStream = outputStream;
            this.enabledSteps = enabledSteps != null ? enabledSteps : getDefaultSteps();
        }

        // Convenience constructor with default steps
        public ProcessingRequest(String inputPath, String outputPath, String password, PrintStream outputStream) {
            this(inputPath, outputPath, password, outputStream, null);
        }

        private static List<PdfAccessibilityFix> getDefaultSteps() {
            return Arrays.asList(
                new TagNormalizationFix(),
                new PdfUaComplianceFix(),
                new TabOrderFix()
            );
        }

        // Getters
        public String getInputPath() { return inputPath; }
        public String getOutputPath() { return outputPath; }
        public String getPassword() { return password; }
        public PrintStream getOutputStream() { return outputStream; }
        public List<PdfAccessibilityFix> getEnabledSteps() { return enabledSteps; }
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

                // Execute each enabled step
                for (PdfAccessibilityFix step : request.getEnabledSteps()) {
                    OperationResult result = step.execute(pdfDoc, request.getOutputStream());

                    if (!result.isSuccess()) {
                        return ProcessingResult.error("Failed at step: " + step.getName() + " - " + result.getMessage());
                    }

                    totalChanges += result.getChangeCount();
                    totalWarnings += result.getWarningCount();
                }

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
