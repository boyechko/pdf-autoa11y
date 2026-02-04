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
package net.boyechko.pdf.autoa11y.ui.cli;

import java.io.PrintStream;
import java.nio.file.*;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.core.ProcessingService;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.ui.OutputFormatter;

public class PdfAutoA11yCLI {

    // Configuration record to hold parsed CLI arguments
    public record CLIConfig(
            Path inputPath,
            Path outputPath,
            String password,
            boolean force_save,
            boolean reportOnly,
            VerbosityLevel verbosity) {
        public CLIConfig {
            if (inputPath == null) {
                throw new IllegalArgumentException("Input path is required");
            }
            if (!reportOnly && outputPath == null) {
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
            if (isHelpRequested(args)) {
                System.out.println(usageMessage());
                return;
            }
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
            throw new CLIException("No input file specified\n" + usageMessage());
        }

        Path inputPath = null;
        Path outputPath = null;
        String password = null;
        boolean force_save = false;
        boolean reportOnly = false;
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
                case "-r", "--report" -> reportOnly = true;
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
        if (!reportOnly) {
            String filename = inputPath.getFileName().toString();
            if (outputPath == null) {
                outputPath =
                        Paths.get(filename.replaceFirst("(_a11y)*[.][^.]+$", "") + "_autoa11y.pdf");
            }
        } else if (verbosity == VerbosityLevel.NORMAL) {
            verbosity = VerbosityLevel.VERBOSE;
        }

        return new CLIConfig(inputPath, outputPath, password, force_save, reportOnly, verbosity);
    }

    private static void configureLogging(VerbosityLevel verbosity) {
        if (verbosity.isAtLeast(VerbosityLevel.DEBUG)) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        }
    }

    private static void processFile(CLIConfig config) {
        PrintStream output = System.out;
        boolean closeOutput = false;

        try {
            if (config.reportOnly() && config.outputPath() != null) {
                Path outputParent = config.outputPath().getParent();
                if (outputParent != null) {
                    Files.createDirectories(outputParent);
                }
                output = new PrintStream(Files.newOutputStream(config.outputPath()));
                closeOutput = true;
            }

            VerbosityLevel verbosity = config.verbosity();
            OutputFormatter formatter = new OutputFormatter(output, verbosity);

            ProcessingService service =
                    new ProcessingService(
                            config.inputPath(), config.password(), formatter, verbosity);

            if (config.reportOnly()) {
                service.analyzeOnly();
                return;
            }
            ProcessingResult result = service.process();

            if (result.totalIssuesResolved() == 0 && !config.force_save()) {
                formatter.onInfo("No changes made; output file not created");
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

            formatter.onSuccess("Output saved to " + config.outputPath().toString());
        } catch (Exception e) {
            if (isDevelopment() || config.verbosity().isAtLeast(VerbosityLevel.DEBUG)) {
                System.err.println("✗ Processing failed:");
                e.printStackTrace();
            } else {
                System.err.println("Processing failed: " + e.getMessage());
            }
        } finally {
            if (closeOutput) {
                output.close();
            }
        }
    }

    private static boolean isDevelopment() {
        return "true".equals(System.getProperty("debug"));
    }

    private static boolean isHelpRequested(String[] args) {
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String usageMessage() {
        return "Usage: java PdfAutoA11yCLI [-q|-v|-vv] [-f] [-p password] [-r] <inputpath> [<outputpath>]\n"
                + "  -h, --help     Show this help message\n"
                + "  -q, --quiet    Only show errors and final status\n"
                + "  -v, --verbose  Show detailed tag structure\n"
                + "  -vv, --debug   Show all debug information\n"
                + "  -f, --force    Force save even if no fixes applied\n"
                + "  -p, --password Password for encrypted PDFs\n"
                + "  -r, --report   Validate tag structure only (report defaults to verbose)\n"
                + "Example: java PdfAutoA11yCLI -v document.pdf output.pdf";
    }
}
