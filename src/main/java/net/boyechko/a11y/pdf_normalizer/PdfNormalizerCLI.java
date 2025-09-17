package net.boyechko.a11y.pdf_normalizer;

import java.io.*;
import java.nio.file.*;

public class PdfNormalizerCLI {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java PdfNormalizerCLI [-p password] <filepath>");
            System.err.println("Example: java PdfNormalizerCLI -p somepassword document.pdf");
            System.exit(1);
        }

        String password = null;
        String inputPath = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                password = args[i + 1];
                i++;
            } else if (inputPath == null) {
                inputPath = args[i];
            }
        }
        
        if (inputPath == null) {
            System.err.println("Error: No input file specified");
            System.exit(1);
        }

        // Handle path resolution
        Path srcPath = Paths.get(inputPath);
        if (!srcPath.isAbsolute() && !Files.exists(srcPath)) {
            System.err.println("Error: File not found - " + inputPath);
            System.exit(1);
        }
        
        String filename = srcPath.getFileName().toString();
        String dest = filename.replaceFirst("(_a11y)*[.][^.]+$", "") + "_autoa11y.pdf";
        
        // Print header
        printHeader(srcPath);

        // Process using the service
        PdfProcessingService service = new PdfProcessingService();
        PdfProcessingService.ProcessingRequest request = 
            new PdfProcessingService.ProcessingRequest(srcPath, Paths.get(dest), password, System.out);
        
        PdfProcessingService.ProcessingResult result = service.processPdf(request);
        
        if (result.isSuccess()) {
            printSummary(result, dest);
        } else {
            System.err.println("Error: " + result.getErrorMessage());
            System.exit(1);
        }
    }
    
    private static void printHeader(Path srcPath) {
        System.out.println("=== PDF ACCESSIBILITY TAG NORMALIZER ===");
        System.out.println("Processing: " + srcPath.getFileName());
        System.out.println("Source: " + srcPath.toAbsolutePath().toString());
        System.out.println();
    }
    
    private static void printSummary(PdfProcessingService.ProcessingResult result, String outputPath) {
        System.out.println();
        System.out.println("=== REMEDIATION SUMMARY ===");
        
        int changes = result.getChangeCount();
        int warnings = result.getWarningCount();
        
        if (changes == 0 && warnings == 0) {
            System.out.println("✓ Document structure is already compliant - no changes needed");
        } else {
            System.out.println("✓ Automated fixes applied: " + changes);
            if (warnings > 0) {
                System.out.println("⚠ Manual review needed for: " + warnings + " items");
            }
        }
        System.out.println("Output saved to: " + outputPath);
    }
}