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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;

/**
 * Generates a structured plain-text accessibility report from processing results. The report serves
 * as a remediation audit trail: what was found, what was fixed, and what remains.
 */
public final class AccessibilityReport {

    private AccessibilityReport() {}

    /**
     * Writes a plain-text accessibility report to the given path.
     *
     * @param result the processing result containing all issue data
     * @param inputPath the path to the original PDF
     * @param outputPath the path to the remediated PDF (may be null if not saved)
     * @param reportPath the path where the report file will be written
     */
    public static void write(
            ProcessingResult result, Path inputPath, Path outputPath, Path reportPath)
            throws IOException {
        Path reportParent = reportPath.getParent();
        if (reportParent != null) {
            Files.createDirectories(reportParent);
        }

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            writeHeader(out, inputPath, outputPath);
            writeSummary(out, result);

            if (result.totalIssuesDetected() == 0) {
                out.println("No accessibility issues were detected.");
                return;
            }

            if (!result.originalDocumentIssues().isEmpty()) {
                writeSection(out, "Document-Level Issues", result.originalDocumentIssues());
            }
            if (!result.originalTagIssues().isEmpty()) {
                writeSection(out, "Structure Tree Issues", result.originalTagIssues());
            }
        }
    }

    private static void writeHeader(PrintWriter out, Path inputPath, Path outputPath) {
        out.println("PDF Accessibility Remediation Report");
        out.println("=====================================");
        out.println("Input:    " + inputPath);
        if (outputPath != null) {
            out.println("Output:   " + outputPath);
        }
        out.println("Date:     " + LocalDate.now());
        out.println();
    }

    private static void writeSummary(PrintWriter out, ProcessingResult result) {
        out.println("Summary");
        out.println("-------");
        out.println("Issues detected:  " + result.totalIssuesDetected());
        out.println("Issues resolved:  " + result.totalIssuesResolved());
        out.println("Issues remaining: " + result.totalIssuesRemaining());
        out.println();
    }

    /**
     * Writes a section (document-level or structure tree) grouping issues by type. Each group shows
     * its status, count, group label, and indented details.
     */
    private static void writeSection(PrintWriter out, String heading, IssueList issues) {
        out.println(heading);
        out.println("-".repeat(heading.length()));

        Map<IssueType, List<Issue>> grouped =
                issues.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Issue::type, LinkedHashMap::new, Collectors.toList()));

        for (var entry : grouped.entrySet()) {
            IssueType type = entry.getKey();
            List<Issue> group = entry.getValue();

            List<Issue> resolved = group.stream().filter(Issue::isResolved).toList();
            List<Issue> failed = group.stream().filter(Issue::hasFailed).toList();
            List<Issue> remaining =
                    group.stream().filter(i -> !i.isResolved() && !i.hasFailed()).toList();

            if (!resolved.isEmpty()) {
                writeGroup(out, "[RESOLVED]", type.groupLabel(), resolved, true);
            }
            if (!failed.isEmpty()) {
                writeGroup(out, "[FAILED]", type.groupLabel(), failed, false);
            }
            if (!remaining.isEmpty()) {
                writeGroup(out, "[REMAINING]", type.groupLabel(), remaining, false);
            }
        }
        out.println();
    }

    /**
     * Writes a single group of issues with a status prefix and count. For resolved groups, shows
     * resolution notes; for remaining/failed groups, shows issue messages with location.
     */
    private static void writeGroup(
            PrintWriter out,
            String status,
            String groupLabel,
            List<Issue> issues,
            boolean showResolutionNotes) {
        out.println(status + " " + issues.size() + " " + groupLabel);

        if (showResolutionNotes) {
            for (Issue issue : issues) {
                if (issue.resolutionNote() != null) {
                    out.println("  -> " + issue.resolutionNote());
                }
            }
        } else {
            for (Issue issue : issues) {
                out.println("  - " + issue.message() + Format.loc(issue.where()));
            }
        }
    }
}
