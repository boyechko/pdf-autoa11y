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
        System.out.println("=== PDF AUTO A11Y ===");
        System.out.println("Processing: " + config.inputPath().toString());
        System.out.println();

        // Process using the service
        ProcessingService service = new ProcessingService(
            config.inputPath(),
            config.outputPath(),
            config.password(),
            System.out
        );

        try {
            service.process();
            System.out.println("Output saved to: " + config.outputPath());
        } catch (Exception e) {
            if (isDevelopment()) {
                e.printStackTrace();
            } else {
                System.err.println("Processing failed: " + e.getMessage());
            }
        }
    }

    private static boolean isDevelopment() {
        return "true".equals(System.getProperty("debug"));
    }
}
