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
package net.boyechko.pdf.autoa11y.ui;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.core.ProcessingDefaults;
import net.boyechko.pdf.autoa11y.core.ProcessingListener;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.issues.Issue;

public class ProcessingReporter implements ProcessingListener {
    private final PrintStream output;
    private final VerbosityLevel verbosity;

    private static final String SUCCESS = "✓";
    private static final String ERROR = "⛔️";
    private static final String WARNING = "️✗";
    private static final String INFO = "○";

    private static final String INDENT = "│ ";
    private static final String SUBSECTION_MARK = "🞙︎";
    private static final int HEADER_WIDTH = 68;

    private boolean phaseOpen = false;

    public ProcessingReporter(PrintStream output, VerbosityLevel verbosity) {
        this.output = output;
        this.verbosity = verbosity;
    }

    @Override
    public void onPhaseStart(String phaseName) {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            closePhaseBoxIfOpen();
            printBoxHeader(phaseName);
            phaseOpen = true;
        }
    }

    @Override
    public void onSubsection(String header) {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            printEmptyLine();
            printLine(header, SUBSECTION_MARK);
        }
    }

    @Override
    public void onIssueGroup(String groupLabel, List<Issue> issues) {
        if (issues.isEmpty()) return;

        Set<Integer> pages =
                issues.stream()
                        .map(i -> i.where().page())
                        .filter(p -> p != null)
                        .collect(Collectors.toCollection(TreeSet::new));

        String summary = buildGroupSummary(groupLabel, issues.size(), pages);
        onWarning(summary);

        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            for (Issue issue : issues) {
                printLine(issue.message(), WARNING, VerbosityLevel.VERBOSE);
            }
        }
    }

    @Override
    public void onFixGroup(String groupLabel, List<Issue> resolvedIssues) {
        if (resolvedIssues.isEmpty()) return;

        Set<Integer> pages =
                resolvedIssues.stream()
                        .map(i -> i.where().page())
                        .filter(p -> p != null)
                        .collect(Collectors.toCollection(TreeSet::new));

        String summary = buildGroupSummary(groupLabel, resolvedIssues.size(), pages);
        onSuccess(summary);

        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            for (Issue issue : resolvedIssues) {
                if (issue.resolutionNote() != null) {
                    printLine(issue.resolutionNote(), SUCCESS, VerbosityLevel.VERBOSE);
                }
            }
        }
    }

    @Override
    public void onSummary(int detected, int resolved, int remaining) {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            closePhaseBoxIfOpen();
            printBoxHeader("Summary");

            if (detected == 0 && resolved == 0) {
                printLine(
                        "Checked "
                                + ProcessingDefaults.rules().size()
                                + " document-level rules and found no issues",
                        SUCCESS);
                printLine(
                        "Checked "
                                + ProcessingDefaults.visitorSuppliers().size()
                                + " structure tree rules and found no issues",
                        SUCCESS);
            } else {
                printLine("Issues detected: " + detected, WARNING);
                printLine("Resolved: " + resolved, SUCCESS);
                if (remaining > 0) {
                    printLine("Manual review needed: " + remaining, WARNING);
                }
            }
            printBoxFooter();
        }
    }

    @Override
    public void onSuccess(String message) {
        printLine(message, SUCCESS);
    }

    @Override
    public void onIssueFixed(String resolutionNote) {
        onSuccess(resolutionNote);
    }

    @Override
    public void onError(String message) {
        printLine(message, ERROR, VerbosityLevel.QUIET);
    }

    @Override
    public void onWarning(String message) {
        printLine(message, WARNING);
    }

    @Override
    public void onInfo(String message) {
        printLine(message, INFO);
    }

    @Override
    public void onVerboseOutput(String message) {
        output.print(message);
    }

    public boolean shouldShow(VerbosityLevel level) {
        return verbosity.shouldShow(level);
    }

    private void closePhaseBoxIfOpen() {
        if (phaseOpen && verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            printBoxFooter();
            phaseOpen = false;
        }
    }

    private void printBoxHeader(String title) {
        int filler = Math.max(0, HEADER_WIDTH - title.length() - 1);
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println("┌─ " + title + " " + "─".repeat(filler) + "─╮");
            output.println("│");
        }
    }

    private void printBoxFooter() {
        if (verbosity.shouldShow(VerbosityLevel.NORMAL)) {
            output.println("│");
            output.println("└─╯");
        }
    }

    /**
     * Prints an indented line with the given message and icon if the verbosity
     * level is at least the given level.
     */
    private void printLine(String message, String icon, VerbosityLevel level) {
        if (verbosity.shouldShow(level)) {
            output.println(icon == null ? message : INDENT + icon + " " + message);
        }
    }

    private void printLine(String message, String icon) {
        printLine(message, icon, VerbosityLevel.NORMAL);
    }

    private void printEmptyLine() {
        printLine("", "", VerbosityLevel.QUIET);
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

        int min = pages.stream().min(Integer::compareTo).orElse(0);
        int max = pages.stream().max(Integer::compareTo).orElse(0);

        if (max - min + 1 == pages.size()) {
            return min + "-" + max;
        } else {
            return pages.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
    }
}
