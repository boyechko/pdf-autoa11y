package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;

public class PdfNormalizerApp {
    private static ArrayList<String> changesApplied = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java PdfNormalizerApp [-p password] <filepath>");
            System.err.println("Example: java PdfNormalizerApp -p somepassword /path/to/document.pdf");
            System.err.println("Example: java PdfNormalizerApp document.pdf");
            System.exit(1);
        }

        String password = null;
        String inputPath = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                password = args[i + 1];
                i++; // Skip the next argument since it's the password
            } else if (inputPath == null) {
                inputPath = args[i];
            }
        }
        
        if (inputPath == null) {
            System.err.println("Error: No input file specified");
            System.exit(1);
        }

        // Handle both absolute paths and relative filenames
        Path srcPath = Paths.get(inputPath);
        if (!srcPath.isAbsolute() && !Files.exists(srcPath)) {
            // If it's not absolute and doesn't exist in current dir, try inputs/ folder
            srcPath = Paths.get("inputs", inputPath);
        }
        
        String src = srcPath.toString();
        
        // Extract just the filename for output
        String filename = srcPath.getFileName().toString();
        String dest = "outputs/normalized_" + filename;
        
        // Ensure both paths exist
        if (!Files.exists(srcPath)) {
            System.err.println("Error: File not found: " + src);
            System.exit(1);
        }
        
        Files.createDirectories(Paths.get(dest).getParent());

        // First, try to open and check if password-protected
        ReaderProperties readerProps = new ReaderProperties();
        if (password != null) {
            readerProps.setPassword(password.getBytes());
        }

        try {
            // Open for reading to check encryption properties
            PdfReader testReader = new PdfReader(src, readerProps);
            PdfDocument testDoc = new PdfDocument(testReader);
            
            int permissions = testReader.getPermissions();
            int cryptoMode = testReader.getCryptoMode();
            boolean isEncrypted = testReader.isEncrypted();
            
            // Print header with document info
            printHeader(filename, testDoc);
            
            testDoc.close();
            
            // Now open for processing with proper writer properties
            PdfReader pdfReader = new PdfReader(src, readerProps);
            WriterProperties writerProps = new WriterProperties();
            
            if (isEncrypted && password != null) {
                writerProps.setStandardEncryption(
                    null, // user password (null means same as owner)
                    password.getBytes(), // owner password
                    permissions,
                    cryptoMode
                );
            }
            
            writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
            changesApplied.add("Set PDF/UA-1 compliance flag");
            PdfWriter pdfWriter = new PdfWriter(dest, writerProps);
            
            try (PdfDocument pdfDoc = new PdfDocument(pdfReader, pdfWriter)) {
                PdfTagNormalizer processor = new PdfTagNormalizer(pdfDoc);
                processor.processAndDisplayChanges();

                // Set tab order for all pages to structure order
                for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                    pdfDoc.getPage(i).setTabOrder(PdfName.S);
                }
                changesApplied.add("Set tab order to structure order for all pages");

                // Print summary
                printSummary(processor, dest);
            }
            
        } catch (Exception e) {
            if (password == null && e.getMessage().contains("Bad user password")) {
                System.err.println("The PDF is password-protected. Please provide a password as the second argument.");
                System.exit(1);
            } else {
                throw e;
            }
        }
    }
    
    private static void printHeader(String filename, PdfDocument doc) {
        System.out.println("=== PDF ACCESSIBILITY TAG NORMALIZER ===");
        System.out.println("Automatically fixes common PDF accessibility issues:");
        System.out.println("• Converts malformed list structures to proper format");
        System.out.println("• Demotes multiple H1 headings to H2 for proper hierarchy");
        System.out.println("• Standardizes document structure tags");
        System.out.println();
        System.out.println("Processing: " + filename);
        if (doc.getDocumentInfo().getTitle() != null && !doc.getDocumentInfo().getTitle().isEmpty()) {
            System.out.println("Document title: " + doc.getDocumentInfo().getTitle());
        }
        System.out.println("Pages: " + doc.getNumberOfPages());
        System.out.println();
        System.out.println("Tag structure analysis and fixes:");
        System.out.println("────────────────────────────────────────");
    }
    
    private static void printSummary(PdfTagNormalizer processor, String outputPath) {
        System.out.println();
        System.out.println("=== REMEDIATION SUMMARY ===");
        
        int changes = processor.getChangeCount();
        int warnings = processor.getWarningCount();
        
        if (changes == 0 && warnings == 0) {
            System.out.println("✓ Document structure is already compliant - no changes needed");
        } else {
            System.out.println("✓ Automated fixes applied: " + changes);
            for (String change : changesApplied) {
                System.out.println("✓ " + change);
            }
            if (warnings > 0) {
                System.out.println("⚠ Manual review needed for: " + warnings + " items");
            }
        }
        System.out.println("Output saved to: " + outputPath);
    }
}