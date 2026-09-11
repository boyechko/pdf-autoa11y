// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.RemediationEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemediationLogTest {

    @TempDir Path tempDir;

    private static final RemediationEntry ENTRY =
            new RemediationEntry(97, 1, "Figure", "FigureWithTextFix", "Retagged Figure as H1");

    @Test
    void runRecordPrecedesItsFixRecords() throws IOException {
        List<String> lines = appendAndRead(List.of(ENTRY));

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"t\":\"run\""), lines.get(0));
        assertTrue(lines.get(1).contains("\"t\":\"fix\""), lines.get(1));
    }

    @Test
    void fixRecordCarriesElementIdentityAndDescription() throws IOException {
        String fixLine = appendAndRead(List.of(ENTRY)).get(1);

        assertTrue(fixLine.contains("\"obj\":97"), fixLine);
        assertTrue(fixLine.contains("\"pg\":1"), fixLine);
        assertTrue(fixLine.contains("\"role\":\"Figure\""), fixLine);
        assertTrue(fixLine.contains("\"fix\":\"FigureWithTextFix\""), fixLine);
        assertTrue(fixLine.contains("\"msg\":\"Retagged Figure as H1\""), fixLine);
    }

    @Test
    void everyFixRecordReferencesTheRunThatProducedIt() throws IOException {
        List<String> lines = appendAndRead(List.of(ENTRY, ENTRY));

        String runId = valueOf(lines.get(0), "id");
        assertEquals(runId, valueOf(lines.get(1), "run"));
        assertEquals(runId, valueOf(lines.get(2), "run"));
    }

    @Test
    void secondRunAppendsRatherThanTruncating() throws IOException {
        Path log = tempDir.resolve("catalog.autoa11y.log");
        RemediationLog.append(log, List.of(ENTRY), tempDir.resolve("in.pdf"), null);
        RemediationLog.append(log, List.of(ENTRY), tempDir.resolve("in.pdf"), null);

        assertEquals(4, Files.readAllLines(log).size());
    }

    @Test
    void runWithNoAppliedFixesStillRecordsThatTheToolRan() throws IOException {
        List<String> lines = appendAndRead(List.of());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"t\":\"run\""), lines.get(0));
    }

    @Test
    void quotesAndBackslashesInDescriptionsStayParseable() throws IOException {
        RemediationEntry entry =
                new RemediationEntry(8018, 12, "L", "StaleScribbleFix", "Cleared \"a\\b\" // x");

        String fixLine = appendAndRead(List.of(entry)).get(1);

        assertTrue(fixLine.contains("\\\"a\\\\b\\\""), fixLine);
        assertEquals(1, fixLine.lines().count());
    }

    @Test
    void controlCharactersAreEscapedRatherThanBreakingTheLine() throws IOException {
        RemediationEntry entry =
                new RemediationEntry(1, 1, "P", "SomeFix", "line one\nline two\ttabbed");

        List<String> lines = appendAndRead(List.of(entry));

        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("\\n"), lines.get(1));
        assertTrue(lines.get(1).contains("\\t"), lines.get(1));
    }

    @Test
    void missingLocationFieldsAreRecordedAsNull() throws IOException {
        RemediationEntry entry =
                new RemediationEntry(null, null, null, "LanguageSetFix", "Set language to en");

        String fixLine = appendAndRead(List.of(entry)).get(1);

        assertTrue(fixLine.contains("\"obj\":null"), fixLine);
        assertTrue(fixLine.contains("\"pg\":null"), fixLine);
        assertTrue(fixLine.contains("\"role\":null"), fixLine);
    }

    @Test
    void logPathIsDerivedFromTheDocumentLineage() {
        assertEquals(
                Path.of("/docs/catalog.autoa11y.log"),
                RemediationLog.resolvePath(Path.of("/docs/catalog_autoa11y.pdf")));
    }

    // == Helpers ==========================================================

    private List<String> appendAndRead(List<RemediationEntry> entries) throws IOException {
        Path log = tempDir.resolve("catalog.autoa11y.log");
        RemediationLog.append(
                log, entries, tempDir.resolve("catalog.pdf"), tempDir.resolve("out.pdf"));
        return Files.readAllLines(log);
    }

    /** Pulls a string field's value out of a JSON line without a JSON parser. */
    private static String valueOf(String jsonLine, String key) {
        String marker = "\"" + key + "\":\"";
        int start = jsonLine.indexOf(marker) + marker.length();
        return jsonLine.substring(start, jsonLine.indexOf('"', start));
    }
}
