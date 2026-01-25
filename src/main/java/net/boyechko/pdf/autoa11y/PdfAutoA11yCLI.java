/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y;

import java.nio.file.*;

public class PdfAutoA11yCLI {

    // Configuration record to hold parsed CLI arguments
    public record CLIConfig(
            Path inputPath,
            Path outputPath,
            String password,
            boolean force_save,
            VerbosityLevel verbosity) {
        public CLIConfig {
            if (inputPath == null) {
                throw new IllegalArgumentException("Input path is required");
            }
            if (outputPath == null) {
                throw new IllegalArgumentException("Output path is required");
            }
            if (verbosity == null) {
                throw new IllegalArgumentException("Verbosity level is required");
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
            configureLogging(config.verbosity());
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
            throw new CLIException(
                    "No input file specified\n"
                            + "Usage: java PdfAutoA11yCLI [-q|-v|-vv] [-f] [-p password] <inputpath> [<outputpath>]\n"
                            + "  -q, --quiet    Only show errors and final status\n"
                            + "  -v, --verbose  Show detailed tag structure\n"
                            + "  -vv, --debug   Show all debug information\n"
                            + "  -f, --force    Force save even if no fixes applied\n"
                            + "  -p, --password Password for encrypted PDFs\n"
                            + "Example: java PdfAutoA11yCLI -v document.pdf output.pdf");
        }

        Path inputPath = null;
        Path outputPath = null;
        String password = null;
        boolean force_save = false;
        VerbosityLevel verbosity = VerbosityLevel.NORMAL;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p", "--password" -> {
                    if (i + 1 < args.length) {
                        password = args[++i];
                    } else {
                        throw new CLIException("Password not specified after -p");
                    }
                }
                case "-q", "--quiet" -> verbosity = VerbosityLevel.QUIET;
                case "-v", "--verbose" -> verbosity = VerbosityLevel.VERBOSE;
                case "-vv", "--debug" -> verbosity = VerbosityLevel.DEBUG;
                case "-f", "--force" -> force_save = true;
                default -> {
                    if (inputPath == null) {
                        inputPath = Paths.get(args[i]);
                    } else if (outputPath == null) {
                        outputPath = Paths.get(args[i]);
                    } else {
                        throw new CLIException("Multiple input files specified");
                    }
                }
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
        if (outputPath == null) {
            outputPath =
                    Paths.get(filename.replaceFirst("(_a11y)*[.][^.]+$", "") + "_autoa11y.pdf");
        }

        return new CLIConfig(inputPath, outputPath, password, force_save, verbosity);
    }

    private static void configureLogging(VerbosityLevel verbosity) {
        if (verbosity.isAtLeast(VerbosityLevel.DEBUG)) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        }
    }

    private static void processFile(CLIConfig config) {
        VerbosityLevel verbosity = config.verbosity();

        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            System.out.println("=== PDF AUTO A11Y ===");
            System.out.println("Processing: " + config.inputPath().toString());
            System.out.println();
        }

        // Process using the service
        ProcessingService service =
                new ProcessingService(config.inputPath(), config.password(), System.out, verbosity);

        try {
            ProcessingService.ProcessingResult result = service.process();

            if (result.issues().getResolvedIssues().isEmpty() && !config.force_save()) {
                if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
                    System.out.println("✗ No output file created (original unchanged)");
                }
                return;
            }

            Path outputParent = config.outputPath().getParent();
            if (outputParent != null) {
                Files.createDirectories(outputParent);
            }

            Files.move(
                    result.tempOutputFile(),
                    config.outputPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
                System.out.println("✓ Output saved to: " + config.outputPath());
            } else if (verbosity == VerbosityLevel.QUIET) {
                // In quiet mode, just show the output path
                System.out.println(config.outputPath());
            }
        } catch (Exception e) {
            if (isDevelopment() || verbosity.isAtLeast(VerbosityLevel.DEBUG)) {
                System.err.println("✗ Processing failed:");
                e.printStackTrace();
            } else {
                System.err.println("✗ Processing failed: " + e.getMessage());
            }
        }
    }

    private static boolean isDevelopment() {
        return "true".equals(System.getProperty("debug"));
    }
}
