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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.core.ProcessingService;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.ui.ProcessingReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfAutoA11yCLI {
    private static final String DEFAULT_OUTPUT_SUFFIX = "_autoa11y";

    private static Logger logger;

    // Configuration record to hold parsed CLI arguments
    public record CLIConfig(
            Path inputPath,
            Path outputPath,
            String password,
            boolean force_save,
            boolean analyzeOnly,
            Path reportPath,
            VerbosityLevel verbosity) {
        public CLIConfig {
            if (inputPath == null) {
                throw new IllegalArgumentException("Input path is required");
            }
            if (!analyzeOnly && outputPath == null) {
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
            logger().info(
                            "Starting processing of {} with verbosity level {}",
                            config.inputPath(),
                            config.verbosity());
            processFile(config);
        } catch (CLIException e) {
            System.err.println("Error: " + e.getMessage());
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
        boolean analyzeOnly = false;
        boolean generateReport = false;
        Path reportPath = null;
        VerbosityLevel verbosity = VerbosityLevel.NORMAL;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--report=")) {
                reportPath = Paths.get(args[i].substring("--report=".length()));
                generateReport = true;
            } else if (args[i].startsWith("-r=")) {
                reportPath = Paths.get(args[i].substring("-r=".length()));
                generateReport = true;
            } else {
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
                    case "-a", "--analyze" -> analyzeOnly = true;
                    case "-r", "--report" -> generateReport = true;
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
        }

        if (inputPath == null) {
            throw new CLIException("No input file specified");
        }

        if (!Files.exists(inputPath)) {
            throw new CLIException("File not found: " + inputPath);
        }

        // Compute base name for auto-generated filenames
        String inputBaseName =
                inputPath.getFileName().toString().replaceFirst("(_a11y)*[.][^.]+$", "");

        // Generate output path
        if (!analyzeOnly) {
            if (outputPath == null) {
                String outputFilename = inputBaseName + DEFAULT_OUTPUT_SUFFIX + ".pdf";
                Path parent = inputPath.getParent();
                outputPath =
                        parent != null ? parent.resolve(outputFilename) : Paths.get(outputFilename);
            } else if (Files.isDirectory(outputPath)) {
                outputPath = outputPath.resolve(inputBaseName + DEFAULT_OUTPUT_SUFFIX + ".pdf");
            }
        }

        // Resolve report path
        String reportFilename = inputBaseName + DEFAULT_OUTPUT_SUFFIX + ".txt";
        if (generateReport && reportPath == null) {
            Path reportSibling = analyzeOnly ? inputPath : outputPath;
            reportPath = reportSibling.resolveSibling(reportFilename);
        } else if (reportPath != null && Files.isDirectory(reportPath)) {
            reportPath = reportPath.resolve(reportFilename);
        }

        return new CLIConfig(
                inputPath, outputPath, password, force_save, analyzeOnly, reportPath, verbosity);
    }

    private static void configureLogging(VerbosityLevel verbosity) {
        String level =
                switch (verbosity) {
                    case QUIET -> "error";
                    case NORMAL -> "warn";
                    case VERBOSE -> "info";
                    case DEBUG -> "debug";
                };
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level);
    }

    private static Logger logger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(PdfAutoA11yCLI.class);
        }
        return logger;
    }

    private static void processFile(CLIConfig config) {
        PrintStream output = System.out;
        OutputStream reportFile = null;

        try {
            if (config.reportPath() != null) {
                Path reportParent = config.reportPath().getParent();
                if (reportParent != null) {
                    Files.createDirectories(reportParent);
                }
                logger().info("Saving report to {}", config.reportPath());
                reportFile = Files.newOutputStream(config.reportPath());
                output = new PrintStream(new TeeOutputStream(System.out, reportFile));
            }

            VerbosityLevel verbosity = config.verbosity();
            ProcessingReporter reporter = new ProcessingReporter(output, verbosity);
            PdfCustodian docFactory = new PdfCustodian(config.inputPath(), config.password());

            ProcessingService service =
                    new ProcessingService.ProcessingServiceBuilder()
                            .withPdfCustodian(docFactory)
                            .withListener(reporter)
                            .withVerbosityLevel(verbosity)
                            .build();

            if (config.analyzeOnly()) {
                logger().info("Analyzing document");
                service.analyze();
            } else {
                logger().info("Remediating document");
                ProcessingResult result = service.remediate();

                if (result.totalIssuesResolved() == 0 && !config.force_save()) {
                    reporter.onInfo("No changes made; output file not created");
                    return;
                }

                Path outputParent = config.outputPath().getParent();
                if (outputParent != null) {
                    Files.createDirectories(outputParent);
                }

                logger().info(
                                "Moving temporary output file {} to {}",
                                result.tempOutputFile(),
                                config.outputPath());
                Files.move(
                        result.tempOutputFile(),
                        config.outputPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                reporter.onSuccess("Output saved to " + config.outputPath().toString());
            }
        } catch (Exception e) {
            System.err.println("✗ Processing failed due to an exception:");
            System.err.println();
            e.printStackTrace();
        } finally {
            logger().info("Closing output stream");
            if (reportFile != null) {
                output.flush();
                try {
                    reportFile.close();
                } catch (IOException e) {
                    logger().warn("Failed to close report file", e);
                }
            }
        }
    }

    /** Writes to two output streams simultaneously, like the Unix tee command. */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }
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
        return "Usage: java PdfAutoA11yCLI [-a] [-q|-v|-vv] [-f] [-p password] [-r[=report]] <inputpath> [<outputpath>]\n"
                + "  -h, --help      Show this help message\n"
                + "  -a, --analyze   Analyze only (no remediation or output PDF)\n"
                + "  -q, --quiet     Only show errors and final status\n"
                + "  -v, --verbose   Show detailed tag structure\n"
                + "  -vv, --debug    Show all debug information\n"
                + "  -f, --force     Force save even if no fixes applied\n"
                + "  -p, --password  Password for encrypted PDFs\n"
                + "  -r, --report    Save output to report file (auto-named from input)\n"
                + "                  Use -r=<file> or --report=<file> for a custom path\n"
                + "Examples:\n"
                + "  java PdfAutoA11yCLI -a -v document.pdf\n"
                + "  java PdfAutoA11yCLI -r -v document.pdf\n"
                + "  java PdfAutoA11yCLI --report=report.txt -v document.pdf output.pdf";
    }
}
