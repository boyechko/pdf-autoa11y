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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.core.ProcessingService;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.document.StructureTree;
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
            boolean dumpTreeSimple,
            boolean dumpTreeDetailed,
            Path reportPath,
            VerbosityLevel verbosity,
            boolean printStructureTree,
            Set<String> skipVisitors,
            Set<String> includeOnlyVisitors) {
        public CLIConfig {
            if (inputPath == null) {
                throw new IllegalArgumentException("Input path is required");
            }
            if (!analyzeOnly && !dumpTreeSimple && !dumpTreeDetailed && outputPath == null) {
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

    /** Mutable builder that accumulates parsed CLI arguments and resolves derived paths. */
    static class CLIConfigBuilder {
        Path inputPath;
        Path outputPath;
        String password;
        boolean forceSave;
        boolean analyzeOnly;
        boolean dumpTreeSimple;
        boolean dumpTreeDetailed;
        boolean generateReport;
        Path reportPath;
        VerbosityLevel verbosity = VerbosityLevel.NORMAL;
        boolean printStructureTree;
        Set<String> skipVisitors = Set.of();
        Set<String> includeOnlyVisitors = Set.of();

        CLIConfig build() throws CLIException {
            if (inputPath == null) {
                throw new CLIException("No input file specified");
            }
            if (!Files.exists(inputPath)) {
                throw new CLIException("File not found: " + inputPath);
            }

            String baseName =
                    inputPath.getFileName().toString().replaceFirst("(_a11y)*[.][^.]+$", "");
            resolveOutputPath(baseName);
            resolveReportPath(baseName);

            return new CLIConfig(
                    inputPath,
                    outputPath,
                    password,
                    forceSave,
                    analyzeOnly,
                    dumpTreeSimple,
                    dumpTreeDetailed,
                    reportPath,
                    verbosity,
                    printStructureTree,
                    skipVisitors,
                    includeOnlyVisitors);
        }

        private void resolveOutputPath(String baseName) {
            if (analyzeOnly || dumpTreeSimple || dumpTreeDetailed) {
                return;
            }
            if (outputPath == null) {
                String outputFilename = baseName + DEFAULT_OUTPUT_SUFFIX + ".pdf";
                Path parent = inputPath.getParent();
                outputPath =
                        parent != null ? parent.resolve(outputFilename) : Paths.get(outputFilename);
            } else if (Files.isDirectory(outputPath)) {
                outputPath = outputPath.resolve(baseName + DEFAULT_OUTPUT_SUFFIX + ".pdf");
            }
        }

        private void resolveReportPath(String baseName) {
            String reportFilename = baseName + DEFAULT_OUTPUT_SUFFIX + ".txt";
            if (generateReport && reportPath == null) {
                Path reportSibling = analyzeOnly ? inputPath : outputPath;
                reportPath = reportSibling.resolveSibling(reportFilename);
            } else if (reportPath != null && Files.isDirectory(reportPath)) {
                reportPath = reportPath.resolve(reportFilename);
            }
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

        CLIConfigBuilder b = new CLIConfigBuilder();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--report=")) {
                b.reportPath = Paths.get(args[i].substring("--report=".length()));
                b.generateReport = true;
            } else if (args[i].startsWith("-r=")) {
                b.reportPath = Paths.get(args[i].substring("-r=".length()));
                b.generateReport = true;
            } else if (args[i].startsWith("--skip-visitors=")) {
                b.skipVisitors =
                        parseCommaSeparated(args[i].substring("--skip-visitors=".length()));
            } else if (args[i].startsWith("--include-visitors=")) {
                b.includeOnlyVisitors =
                        parseCommaSeparated(args[i].substring("--include-visitors=".length()));
            } else {
                switch (args[i]) {
                    case "-p", "--password" -> {
                        if (i + 1 < args.length) {
                            b.password = args[++i];
                        } else {
                            throw new CLIException("Password not specified after -p");
                        }
                    }
                    case "--skip-visitors" -> {
                        if (i + 1 < args.length) {
                            b.skipVisitors = parseCommaSeparated(args[++i]);
                        } else {
                            throw new CLIException(
                                    "Visitor names not specified after --skip-visitors");
                        }
                    }
                    case "--include-visitors" -> {
                        if (i + 1 < args.length) {
                            b.includeOnlyVisitors = parseCommaSeparated(args[++i]);
                        } else {
                            throw new CLIException(
                                    "Visitor names not specified after --include-visitors");
                        }
                    }
                    case "-q", "--quiet" -> b.verbosity = VerbosityLevel.QUIET;
                    case "-v", "--verbose" -> b.verbosity = VerbosityLevel.VERBOSE;
                    case "-vv", "--debug" -> b.verbosity = VerbosityLevel.DEBUG;
                    case "-t", "--print-tree" -> b.printStructureTree = true;
                    case "--dump-tree" -> b.dumpTreeDetailed = true;
                    case "--dump-roles" -> b.dumpTreeSimple = true;
                    case "-f", "--force" -> b.forceSave = true;
                    case "-a", "--analyze" -> b.analyzeOnly = true;
                    case "-r", "--report" -> b.generateReport = true;
                    default -> {
                        if (b.inputPath == null) {
                            b.inputPath = Paths.get(args[i]);
                        } else if (b.outputPath == null) {
                            b.outputPath = Paths.get(args[i]);
                        } else {
                            throw new CLIException("Multiple input files specified");
                        }
                    }
                }
            }
        }

        return b.build();
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
        if (config.dumpTreeSimple() || config.dumpTreeDetailed()) {
            dumpTree(config);
            return;
        }

        OutputStream reportFile = null;
        PrintStream output = System.out;

        try {
            reportFile = openReportStream(config);
            if (reportFile != null) {
                output = new PrintStream(new TeeOutputStream(System.out, reportFile));
            }

            ProcessingReporter reporter = new ProcessingReporter(output, config.verbosity());
            PdfCustodian docFactory = new PdfCustodian(config.inputPath(), config.password());

            ProcessingService service =
                    new ProcessingService.ProcessingServiceBuilder()
                            .withPdfCustodian(docFactory)
                            .withListener(reporter)
                            .withPrintStructureTree(config.printStructureTree())
                            .skipVisitors(config.skipVisitors())
                            .includeOnlyVisitors(config.includeOnlyVisitors())
                            .build();

            if (config.analyzeOnly()) {
                logger().info("Analyzing document");
                service.analyze();
            } else {
                logger().info("Remediating document");
                ProcessingResult result = service.remediate();
                saveRemediationResult(result, config, reporter);
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

    private static OutputStream openReportStream(CLIConfig config) throws IOException {
        if (config.reportPath() == null) {
            return null;
        }
        Path reportParent = config.reportPath().getParent();
        if (reportParent != null) {
            Files.createDirectories(reportParent);
        }
        logger().info("Saving report to {}", config.reportPath());
        return Files.newOutputStream(config.reportPath());
    }

    private static void saveRemediationResult(
            ProcessingResult result, CLIConfig config, ProcessingReporter reporter)
            throws IOException {
        if (result.tempOutputFile() == null) {
            return;
        }

        if (result.totalIssuesResolved() == 0 && !config.force_save()) {
            reporter.onInfo("No changes made; output file not created");
            return;
        }

        Path outputParent = config.outputPath().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        logger().info(
                        "Copying temporary output file {} to {}",
                        result.tempOutputFile(),
                        config.outputPath());
        Files.copy(
                result.tempOutputFile(), config.outputPath(), StandardCopyOption.REPLACE_EXISTING);

        reporter.onSuccess("Output saved to " + config.outputPath().toString());
    }

    /** Prints the structure tree to the console based on the CLI config. */
    private static void dumpTree(CLIConfig config) {
        try {
            PdfCustodian custodian = new PdfCustodian(config.inputPath(), config.password());
            try (PdfDocument pdfDoc = custodian.openForReading()) {
                PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
                if (root == null) {
                    System.err.println("✗ PDF has no structure tree");
                    System.exit(1);
                }
                PdfStructElem docElem = StructureTree.findDocument(root);
                if (docElem == null) {
                    System.err.println("✗ Structure tree has no Document element");
                    System.exit(1);
                }
                if (config.dumpTreeDetailed()) {
                    Map<Integer, Set<Content.ContentKind>> contentKinds = new HashMap<>();
                    for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                        PdfPage page = pdfDoc.getPage(i);
                        contentKinds.putAll(Content.extractContentKindsForPage(page));
                    }
                    System.out.print(StructureTree.toDetailedTreeString(docElem, contentKinds));
                } else {
                    System.out.print(StructureTree.toIndentedTreeString(docElem));
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Failed to read PDF: " + e.getMessage());
            System.exit(1);
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

    private static Set<String> parseCommaSeparated(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        return "Usage: java PdfAutoA11yCLI [-a] [-q|-v|-vv] [-t] [-f] [-p password] [-r[=report]] <inputpath> [<outputpath>]\n"
                + "  -h, --help        Show this help message\n"
                + "  -a, --analyze     Analyze only (no remediation or output PDF)\n"
                + "  -q, --quiet       Only show errors and final status\n"
                + "  -v, --verbose     Show detailed processing information\n"
                + "  -vv, --debug      Show all debug information\n"
                + "  -t, --print-tree  Print the structure tree during processing\n"
                + "  --dump-tree       Print the structure tree (with MCRs and annotations) and exit\n"
                + "  --dump-roles      Print the structure tree (roles only) and exit\n"
                + "  -f, --force       Force save even if no fixes applied\n"
                + "  -p, --password    Password for encrypted PDFs\n"
                + "  -r, --report      Save output to report file (auto-named from input)\n"
                + "                    Use -r=<file> or --report=<file> for a custom path\n"
                + "  --skip-visitors <names>     Skip specific visitors (comma-separated class names)\n"
                + "  --include-visitors <names>  Run only these visitors (comma-separated class names)\n"
                + "Examples:\n"
                + "  java PdfAutoA11yCLI -a -v document.pdf\n"
                + "  java PdfAutoA11yCLI -r -t document.pdf\n"
                + "  java PdfAutoA11yCLI --dump-tree document.pdf\n"
                + "  java PdfAutoA11yCLI --report=report.txt -v document.pdf output.pdf\n"
                + "  java PdfAutoA11yCLI --skip-visitors=NeedlessNestingVisitor,PagePartVisitor document.pdf";
    }
}
