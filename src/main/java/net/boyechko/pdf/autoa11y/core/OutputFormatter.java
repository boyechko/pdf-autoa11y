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
package net.boyechko.pdf.autoa11y.core;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.issues.Issue;

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

    private void printLine(
            String message, String icon, Boolean newlineBefore, VerbosityLevel level) {
        if (verbosity.shouldShow(level)) {
            if (newlineBefore) output.println();
            output.println(icon == null ? message : "  " + icon + " " + message);
        }
    }

    private void printLine(String message, String icon, Boolean newlineBefore) {
        printLine(message, icon, newlineBefore, VerbosityLevel.NORMAL);
    }

    private void printLine(String message, String icon) {
        printLine(message, icon, false, VerbosityLevel.NORMAL);
    }

    public void printHeader(String inputPath) {
        printLine("", null);
    }

    public void printPhase(int step, int total, String phaseName) {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println();
            output.printf("[%d/%d] %s...%n", step, total, phaseName);
            if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
                output.println();
            }
        }
    }

    public void printSubsection(String header) {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println();
            output.println("  " + header);
        }
    }

    public void printSeparator() {
        printLine(SECTION_LINE, null);
    }

    public void printSuccess(String message) {
        printLine(message, SUCCESS);
    }

    public void printError(String message) {
        printLine(message, ERROR, false, VerbosityLevel.QUIET);
    }

    public void printWarning(String message) {
        printLine(message, WARNING);
    }

    public void printInfo(String message) {
        printLine(message, INFO);
    }

    public void printDetail(String message) {
        printLine("    " + message, null);
    }

    public void printSummary(int detected, int resolved, int remaining) {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println();
            output.println(SECTION_LINE);
            output.println("Summary");
            output.println(SECTION_LINE);

            if (detected == 0 && resolved == 0) {
                printLine("No issues to fix", SUCCESS);
            } else {
                printLine("Issues detected: " + detected, WARNING);
                printLine("Resolved: " + resolved, SUCCESS);
                if (remaining > 0) {
                    printLine("Manual review needed: " + remaining, WARNING);
                }
            }
        }
    }

    public void printCompletion(String outputPath) {
        if (verbosity == VerbosityLevel.QUIET) {
            output.println(outputPath);
        } else if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            printLine("Output saved to " + outputPath, SUCCESS);
        }
    }

    public void printNoOutput() {
        printLine("No changes made; output file not created", INFO);
    }

    public PrintStream getStream() {
        return output;
    }

    public boolean shouldShow(VerbosityLevel level) {
        return verbosity.shouldShow(level);
    }

    public void printIssueGroup(String groupLabel, List<Issue> issues) {
        if (issues.isEmpty()) return;

        Set<Integer> pages =
                issues.stream()
                        .map(i -> i.where().page())
                        .filter(p -> p != null)
                        .collect(Collectors.toCollection(TreeSet::new));

        String summary = buildGroupSummary(groupLabel, issues.size(), pages);
        printWarning(summary);

        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            for (Issue issue : issues) {
                printDetail(issue.message());
            }
        }
    }

    public void printFixGroup(String groupLabel, List<Issue> resolvedIssues) {
        if (resolvedIssues.isEmpty()) return;

        Set<Integer> pages =
                resolvedIssues.stream()
                        .map(i -> i.where().page())
                        .filter(p -> p != null)
                        .collect(Collectors.toCollection(TreeSet::new));

        String summary = buildGroupSummary(groupLabel, resolvedIssues.size(), pages);
        printSuccess(summary);

        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            for (Issue issue : resolvedIssues) {
                if (issue.resolutionNote() != null) {
                    printDetail(issue.resolutionNote());
                }
            }
        }
    }

    private String buildGroupSummary(String groupLabel, int count, Set<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(" ").append(groupLabel);

        if (!pages.isEmpty()) {
            sb.append(" (");
            if (pages.size() == 1) {
                sb.append("page ").append(pages.iterator().next());
            } else {
                sb.append("pages ").append(formatPageRange(pages));
            }
            sb.append(")");
        }

        return sb.toString();
    }

    private String formatPageRange(Set<Integer> pages) {
        if (pages.isEmpty()) return "";
        if (pages.size() == 1) return pages.iterator().next().toString();

        // In future, could detect consecutive runs for smarter ranges
        int min = pages.stream().min(Integer::compareTo).orElse(0);
        int max = pages.stream().max(Integer::compareTo).orElse(0);

        if (max - min + 1 == pages.size()) {
            return min + "-" + max;
        } else {
            return min + "-" + max + " (non-consecutive)";
        }
    }
}
