package net.boyechko.pdf.autoa11y;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import com.itextpdf.kernel.pdf.*;

public class ProcessingService {

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
        private final int issueCount;
        private final int changeCount;
        private final int warningCount;
        private final String errorMessage;

        private ProcessingResult(boolean success, int issueCount, int changeCount, int warningCount, String errorMessage) {
            this.success = success;
            this.issueCount = issueCount;
            this.changeCount = changeCount;
            this.warningCount = warningCount;
            this.errorMessage = errorMessage;
        }

        public static ProcessingResult success(int issueCount, int changeCount, int warningCount) {
            return new ProcessingResult(true, issueCount, changeCount, warningCount, null);
        }

        public static ProcessingResult error(String errorMessage) {
            return new ProcessingResult(false, 0, 0, 0, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public int getIssueCount() { return issueCount; }
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
                int totalIssues = 0;

                // Step 0: Validate the tag structure
                request.getOutputStream().println("Validating existing tag structure:");
                request.getOutputStream().println("────────────────────────────────────────");
                TagValidator validator = new TagValidator(TagSchema.minimalLists());
                List<Issue> tagIssues = validator.validate(pdfDoc.getStructTreeRoot());
                for (Issue issue : tagIssues) {
                    request.getOutputStream().println("Issue at " + issue.nodePath() + ": " + issue.message());
                }
                if (tagIssues.isEmpty()) {
                    request.getOutputStream().println("✓ No issues found in tag structure");
                } else {
                    request.getOutputStream().println("Tag issues found: " + tagIssues.size());
                    totalIssues += tagIssues.size();
                }

                PdfCatalog cat = pdfDoc.getCatalog();
                PdfNameTree markInfo = cat.getNameTree(PdfName.MarkInfo);
                PdfObject isMarked = markInfo.getEntry("Marked");
                if (isMarked == null || !isMarked.toString().equals("true")) {
                    request.getOutputStream().println("✗ Document is not marked as tagged PDF");
                    totalIssues++;
                } else {
                    request.getOutputStream().println("✓ Document is marked as tagged PDF");
                }

                if (cat.getLang() == null) {
                    request.getOutputStream().println("✗ Document language (Lang) is not set");
                    totalIssues++;
                } else {
                    request.getOutputStream().println("✓ Document language (Lang) is set to: " + cat.getLang());
                }

                request.getOutputStream().println();

                // Step 1: Tag structure normalization
                request.getOutputStream().println("Tag structure analysis and fixes:");
                request.getOutputStream().println("────────────────────────────────────────");

                TagNormalizer normalizer = new TagNormalizer(pdfDoc, request.getOutputStream());
                normalizer.processAndDisplayChanges();

                totalChanges += normalizer.getChangeCount();
                totalWarnings += normalizer.getWarningCount();

                // Step 2: Set document as tagged PDF & PDF/UA-1 compliant
                markInfo.addEntry("Marked", PdfBoolean.TRUE);
                request.getOutputStream().println("✓ Set document as tagged PDF");
                // PDF/UA-1 compliance flag is set via writer properties earlier
                request.getOutputStream().println("✓ Set PDF/UA-1 compliance flag");
                totalChanges++;

                // Step 2b: Ensure document language is set
                if (cat.getLang() == null) {
                    cat.setLang(new PdfString("en-US"));
                    request.getOutputStream().println("✓ Set document language (Lang) to en-US");
                    totalChanges++;
                }

                // Step 3: Tab order
                int pageCount = pdfDoc.getNumberOfPages();
                for (int i = 1; i <= pageCount; i++) {
                    pdfDoc.getPage(i).setTabOrder(PdfName.S);
                }
                request.getOutputStream().println("✓ Set tab order to structure order for all " + pageCount + " pages");

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
