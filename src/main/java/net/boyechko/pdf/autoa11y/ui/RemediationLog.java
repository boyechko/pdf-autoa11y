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
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.RemediationEntry;
import net.boyechko.pdf.autoa11y.core.VersionInfo;

/**
 * Append-only per-document record of which fix changed which element, and when.
 *
 * <p>The log accumulates across runs in {@code <base>.autoa11y.log}, one JSON object per line: a
 * {@code run} record naming the tool version and the files involved, followed by a {@code fix}
 * record per changed element referencing that run's id.
 *
 * <p>Elements are anchored by PDF object number, which survives the remediation pipeline (see
 * docs/decisions/0007-keep-remediation-history-in-a-sidecar-log.md). Because the log is append-only
 * and fixes may delete elements, it is a history rather than an index: entries can point at objects
 * no longer present in the current output.
 */
public final class RemediationLog {
    private static final String LOG_EXTENSION = ".autoa11y.log";

    private RemediationLog() {}

    /** Returns the log path for the given PDF, shared by every document in its lineage. */
    public static Path resolvePath(Path pdfPath) {
        return SidecarPaths.forPdf(pdfPath, LOG_EXTENSION);
    }

    /**
     * Appends one run's worth of entries to {@code logPath}, creating the file if needed. The run
     * record is written even when no fixes applied, so the log shows that the tool ran.
     */
    public static void append(
            Path logPath, List<RemediationEntry> entries, Path inputPath, Path outputPath)
            throws IOException {
        String runId = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

        List<String> lines = new ArrayList<>(entries.size() + 1);
        lines.add(runRecord(runId, inputPath, outputPath));
        for (RemediationEntry entry : entries) {
            lines.add(fixRecord(runId, entry));
        }

        Files.write(
                logPath,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    // == Record formatting ================================================

    private static String runRecord(String runId, Path inputPath, Path outputPath) {
        return "{\"t\":\"run\""
                + field("id", runId)
                + field("tool", VersionInfo.current().version())
                + field("in", fileNameOf(inputPath))
                + field("out", fileNameOf(outputPath))
                + "}";
    }

    private static String fixRecord(String runId, RemediationEntry entry) {
        return "{\"t\":\"fix\""
                + field("run", runId)
                + field("obj", entry.objNum())
                + field("pg", entry.pageNum())
                + field("role", entry.role())
                + field("fix", entry.fixClass())
                + field("msg", entry.message())
                + "}";
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
