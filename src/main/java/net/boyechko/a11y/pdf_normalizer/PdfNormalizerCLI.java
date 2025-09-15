package net.boyechko.a11y.pdf_normalizer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import net.boyechko.a11y.pdf_normalizer.fixes.PdfUaComplianceFix;
import net.boyechko.a11y.pdf_normalizer.fixes.TabOrderFix;
import net.boyechko.a11y.pdf_normalizer.fixes.TagNormalizationFix;

public class PdfNormalizerCLI {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java PdfNormalizerCLI [-p password] <filepath>");
            System.err.println("Example: java PdfNormalizerCLI -p somepassword /path/to/document.pdf");
            System.err.println("Example: java PdfNormalizerCLI document.pdf");
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
            srcPath = Paths.get("inputs", inputPath);
        }
        
        String src = srcPath.toString();
        String filename = srcPath.getFileName().toString();
        String dest = "outputs/normalized_" + filename;
        
        // Print header
        printHeader(filename, src);

        // Create default processing steps
        List<PdfAccessibilityFix> steps = Arrays.asList(
            new TagNormalizationFix(),
            new PdfUaComplianceFix(),
            new TabOrderFix()
        );
        
        // Process using the service with steps
        PdfProcessingService service = new PdfProcessingService();
        PdfProcessingService.ProcessingRequest request = 
            new PdfProcessingService.ProcessingRequest(src, dest, password, System.out, steps);
        
        PdfProcessingService.ProcessingResult result = service.processPdf(request);
        
        if (result.isSuccess()) {
            printSummary(result, dest);
        } else {
            System.err.println("Error: " + result.getErrorMessage());
            System.exit(1);
        }
    }
    
    private static void printHeader(String filename, String src) {
        System.out.println("=== PDF ACCESSIBILITY TAG NORMALIZER ===");
        System.out.println("Automatically fixes common PDF accessibility issues:");
        System.out.println("• Converts malformed list structures to proper format");
        System.out.println("• Demotes multiple H1 headings to H2 for proper hierarchy");
        System.out.println("• Standardizes document structure tags");
        System.out.println();
        System.out.println("Processing: " + filename);
        System.out.println("Source: " + src);
        System.out.println();
        System.out.println("Tag structure analysis and fixes:");
        System.out.println("────────────────────────────────────────");
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
            System.out.println("✓ Set PDF/UA-1 compliance flag");
            System.out.println("✓ Set tab order to structure order for all pages");
            if (warnings > 0) {
                System.out.println("⚠ Manual review needed for: " + warnings + " items");
            }
        }
        System.out.println("Output saved to: " + outputPath);
    }
}