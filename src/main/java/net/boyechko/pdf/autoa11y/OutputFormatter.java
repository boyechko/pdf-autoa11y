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

import java.io.PrintStream;

/** Provides consistent formatting for CLI output with visual hierarchy. */
public class OutputFormatter {
    private final PrintStream output;
    private final VerbosityLevel verbosity;

    private static final String SUCCESS = "✓";
    private static final String ERROR = "✗";
    private static final String WARNING = "⚠";
    private static final String INFO = "ℹ";

    private static final String SECTION_LINE = "─".repeat(50);

    public OutputFormatter(PrintStream output, VerbosityLevel verbosity) {
        this.output = output;
        this.verbosity = verbosity;
    }

    public void printHeader(String inputPath) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println();
    }

    public void printPhase(int step, int total, String phaseName) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println();
        output.printf("[%d/%d] %s...%n", step, total, phaseName);
        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            output.println();
        }
    }

    public void printSeparator() {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println(SECTION_LINE);
    }

    public void printSuccess(String message) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println("  " + SUCCESS + " " + message);
    }

    public void printError(String message) {
        output.println("  " + ERROR + " " + message);
    }

    public void printWarning(String message) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println("  " + WARNING + " " + message);
    }

    public void printInfo(String message) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println("  " + INFO + " " + message);
    }

    public void printDetail(String message) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }
        output.println("    " + message);
    }

    public void printSummary(int detected, int resolved, int remaining) {
        if (!verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            return;
        }

        output.println();
        output.println(SECTION_LINE);
        output.println("Summary");
        output.println(SECTION_LINE);

        if (detected == 0 && resolved == 0) {
            output.println(SUCCESS + " Document is already compliant");
        } else {
            output.println("  Issues detected: " + detected);
            output.println("  " + SUCCESS + " Resolved: " + resolved);
            if (remaining > 0) {
                output.println("  " + WARNING + " Manual review needed: " + remaining);
            }
        }
    }

    public void printCompletion(String outputPath) {
        if (verbosity == VerbosityLevel.QUIET) {
            output.println(outputPath);
        } else if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println(SUCCESS + " Saved to: " + outputPath);
        }
    }

    public void printNoOutput() {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println(INFO + " No changes made - output file not created");
        }
    }

    /** Get the underlying PrintStream for direct access when needed */
    public PrintStream getStream() {
        return output;
    }

    /** Check if output should be shown at the given level */
    public boolean shouldShow(VerbosityLevel level) {
        return verbosity.shouldShow(level);
    }
}
