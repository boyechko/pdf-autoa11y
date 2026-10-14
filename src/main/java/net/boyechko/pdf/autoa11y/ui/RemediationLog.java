// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.core.CheckOutcome;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.file.VersionInfo;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/**
 * Append-only per-document history, with one JSON object per run containing checks and issues.
 *
 * <p>Each issue carries its detection details, location, and outcome, including skipped, failed,
 * and unresolved issues. Object numbers anchor the history to the PDF (see
 * docs/decisions/0007-keep-remediation-history-in-a-sidecar-log.md). Entries may refer to objects
 * deleted by remediation; the log is a history rather than an index.
 */
public final class RemediationLog {
    private static final String LOG_EXTENSION = ".autoa11y.log";

    private RemediationLog() {}

    /** Returns the log path for the given PDF, shared by every document in its lineage. */
    public static Path resolvePath(Path pdfPath) {
        return SidecarPaths.forPdf(pdfPath, LOG_EXTENSION);
    }

    /** Appends one complete run, including checks with no issues, creating the file if needed. */
    public static void append(
            Path logPath, List<CheckOutcome> checks, Path inputPath, Path outputPath)
            throws IOException {
        String runId = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Files.write(
                logPath,
                List.of(runRecord(runId, checks, inputPath, outputPath)),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    // == Record formatting ================================================

    private static String runRecord(
            String runId, List<CheckOutcome> checks, Path inputPath, Path outputPath) {
        return "{\"type\":\"run\""
                + field("id", runId)
                + field("version", VersionInfo.current().version())
                + field("input", fileNameOf(inputPath))
                + field("output", fileNameOf(outputPath))
                + ",\"checks\":"
                + checks.stream()
                        .map(RemediationLog::checkRecord)
                        .collect(Collectors.joining(",", "[", "]"))
                + "}";
    }

    private static String checkRecord(CheckOutcome check) {
        return "{\"check\":"
                + quote(check.check())
                + ",\"issues\":"
                + check.issues().stream()
                        .map(RemediationLog::issueRecord)
                        .collect(Collectors.joining(",", "[", "]"))
                + "}";
    }

    private static String issueRecord(Issue issue) {
        Locus where = issue.where();
        return "{\"type\":"
                + quote(issue.type().name())
                + field("message", issue.message())
                + field("severity", issue.severity().name())
                + field("object", where.objNum())
                + field("page", where.page())
                + field("role", where.role())
                + field("status", issue.status().name().toLowerCase(Locale.ROOT))
                + field("fix", fixNameOf(issue.fix()))
                + field(
                        "resolution",
                        issue.resolution() != null ? issue.resolution().message() : null)
                + "}";
    }

    /**
     * Names the fix class. Several checks declare their fix anonymously, and an anonymous class has
     * a blank simple name, so those fall back to the enclosing check's name.
     */
    private static String fixNameOf(IssueFix fix) {
        if (fix == null) {
            return null;
        }
        Class<?> fixClass = fix.getClass();
        String name = fixClass.getSimpleName();
        if (!name.isEmpty()) {
            return name;
        }
        Class<?> enclosing = fixClass.getEnclosingClass();
        return enclosing != null ? enclosing.getSimpleName() : fixClass.getName();
    }

    private static String fileNameOf(Path path) {
        return path != null ? path.getFileName().toString() : null;
    }

    private static String field(String key, Integer value) {
        return ",\"" + key + "\":" + (value != null ? value.toString() : "null");
    }

    private static String field(String key, String value) {
        return ",\"" + key + "\":" + quote(value);
    }

    /** Renders a JSON string literal, or {@code null} for a missing value. */
    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
