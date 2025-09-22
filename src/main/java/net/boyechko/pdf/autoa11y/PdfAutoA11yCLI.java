package net.boyechko.pdf.autoa11y;

import java.io.*;
import java.nio.file.*;

public class PdfAutoA11yCLI {

    // Configuration record to hold parsed CLI arguments
    public record CLIConfig(
        Path inputPath,
        Path outputPath,
        String password
    ) {
        public CLIConfig {
            if (inputPath == null) {
                throw new IllegalArgumentException("Input path is required");
            }
            if (outputPath == null) {
                throw new IllegalArgumentException("Output path is required");
            }
        }
    }

    // Custom exception for CLI errors
    public static class CLIException extends Exception {
        public CLIException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            CLIConfig config = parseArguments(args);
            processFile(config);
        } catch (CLIException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Processing error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static CLIConfig parseArguments(String[] args) throws CLIException {
        if (args.length == 0) {
            throw new CLIException("No input file specified\n" +
                "Usage: java PdfAutoA11yCLI [-p password] <filepath>\n" +
                "Example: java PdfAutoA11yCLI -p somepassword document.pdf");
        }

        Path inputPath = null;
        String password = null;

        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                password = args[i + 1];
                i++;
            } else if (inputPath == null) {
                inputPath = Paths.get(args[i]);
            }
        }

        if (inputPath == null) {
            throw new CLIException("No input file specified");
        }

        if (!Files.exists(inputPath)) {
            throw new CLIException("File not found: " + inputPath);
        }

        // Generate output path
        String filename = inputPath.getFileName().toString();
        Path outputPath = Paths.get(filename.replaceFirst("(_a11y)*[.][^.]+$", "") + "_autoa11y.pdf");

        return new CLIConfig(inputPath, outputPath, password);
    }

    private static void processFile(CLIConfig config) {
        printHeader(config.inputPath());

        // Process using the service
        ProcessingService service = new ProcessingService(
            config.inputPath(),
            config.outputPath(),
            config.password(),
            System.out
        );
        ProcessingResult result = service.process();

        if (result.isSuccess()) {
            printSummary(result, config.outputPath());
        } else {
            throw new RuntimeException(result.getErrorMessage());
        }
    }

    private static void printHeader(Path srcPath) {
        System.out.println("=== PDF AUTO A11Y ===");
        System.out.println("Processing: " + srcPath.getFileName());
        System.out.println("Source: " + srcPath.toAbsolutePath().toString());
        System.out.println();
    }

    private static void printSummary(ProcessingResult result, Path outputPath) {
        System.out.println();
        System.out.println("=== REMEDIATION SUMMARY ===");

        int issues = result.getIssueCount();
        int changes = result.getChangeCount();
        int warnings = result.getWarningCount();

        if (issues == 0 && changes == 0 && warnings == 0) {
            System.out.println("✓ Document structure is already compliant");
        } else {
            System.out.println("✗ Issues found: " + issues);
            System.out.println("✓ Automated fixes applied: " + changes);
            if (warnings > 0) {
                System.out.println("⚠ Manual review needed for: " + warnings + " items");
            }
        }
        System.out.println("Output saved to: " + outputPath);
    }
}
