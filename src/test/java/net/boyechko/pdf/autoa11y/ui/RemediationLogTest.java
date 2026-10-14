// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.core.CheckOutcome;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.file.VersionInfo;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class RemediationLogTest {
    @TempDir Path tempDir;

    @Test
    void runContainsChecksAndIssuesInOneRecord() throws IOException {
        List<String> lines =
                appendAndRead(
                        List.of(
                                new CheckOutcome("FigureWithTextCheck", List.of(resolvedIssue())),
                                new CheckOutcome("LanguageSetCheck", List.of())));
        assertEquals(1, lines.size());
        Map<String, Object> run = parse(lines.getFirst());
        assertEquals("run", run.get("type"));
        assertNotNull(run.get("id"));
        assertEquals(VersionInfo.current().version(), run.get("version"));
        assertEquals("catalog.pdf", run.get("input"));
        assertEquals("out.pdf", run.get("output"));
        List<Map<String, Object>> checks = checks(run);
        assertEquals(2, checks.size());
        assertEquals("FigureWithTextCheck", checks.getFirst().get("check"));
        assertEquals(List.of(), checks.getLast().get("issues"));
        Map<String, Object> issue = issues(checks.getFirst()).getFirst();
        assertEquals("FIGURE_WITH_TEXT", issue.get("type"));
        assertEquals("WARNING", issue.get("severity"));
        assertEquals(97, issue.get("object"));
        assertEquals(1, issue.get("page"));
        assertEquals("Figure", issue.get("role"));
        assertEquals("StubFix", issue.get("fix"));
        assertEquals("Figure contains text", issue.get("message"));
        assertEquals("resolved", issue.get("status"));
        assertEquals("Changed Figure to P", issue.get("resolution"));
        assertFalse(issue.containsKey("run"));
    }

    @Test
    void secondRunAppendsRatherThanTruncatingLegacyHistory() throws IOException {
        Path log = tempDir.resolve("catalog.autoa11y.log");
        String legacy = "{\"t\":\"run\",\"id\":\"old\"}";
        Files.write(log, List.of(legacy));
        List<CheckOutcome> checks =
                List.of(new CheckOutcome("FigureWithTextCheck", List.of(resolvedIssue())));
        RemediationLog.append(log, checks, tempDir.resolve("in.pdf"), null);
        RemediationLog.append(log, checks, tempDir.resolve("in.pdf"), null);
        List<String> lines = Files.readAllLines(log);
        assertEquals(3, lines.size());
        assertEquals(legacy, lines.getFirst());
        assertNull(parse(lines.getLast()).get("output"));
        assertEquals(1, checks(parse(lines.getLast())).size());
    }

    @Test
    void runWithNoChecksStillRecordsThatTheToolRan() throws IOException {
        List<String> lines = appendAndRead(List.of());
        assertEquals(1, lines.size());
        assertEquals(List.of(), parse(lines.getFirst()).get("checks"));
    }

    @Test
    void messagesWithQuotesBackslashesAndControlsRoundTripOnOneLine() throws IOException {
        String message = "Cleared \"a\\b\" // x\nline two\ttabbed\r\u0001";
        Issue issue =
                new Issue(
                        IssueType.FIGURE_WITH_TEXT,
                        IssueSev.WARNING,
                        new Locus.AtElem(97, 1, "Figure"),
                        message,
                        new StubFix());
        issue.markResolved(new IssueMsg(message, Locus.none()));

        List<String> lines =
                appendAndRead(List.of(new CheckOutcome("ExampleCheck", List.of(issue))));

        assertEquals(1, lines.size());
        Map<String, Object> logged = firstIssueOf(lines.getFirst());
        assertEquals(message, logged.get("message"));
        assertEquals(message, logged.get("resolution"));
    }

    @Test
    void unresolvedDocumentIssueHasNullLocationFixAndResolution() throws IOException {
        Issue issue = new Issue(IssueType.FIGURE_WITH_TEXT, IssueSev.WARNING, "Needs review");

        Map<String, Object> logged =
                firstIssueOf(
                        appendAndRead(List.of(new CheckOutcome("ExampleCheck", List.of(issue))))
                                .getFirst());

        for (String field : List.of("object", "page", "role", "fix", "resolution")) {
            assertTrue(logged.containsKey(field));
            assertNull(logged.get(field));
        }
        assertEquals("unresolved", logged.get("status"));
    }

    @Test
    void anonymousFixIsNamedAfterItsEnclosingClass() throws IOException {
        IssueFix anonymous =
                new IssueFix() {
                    @Override
                    public void apply(DocContext ctx) {}
                };
        Issue issue =
                new Issue(IssueType.FIGURE_WITH_TEXT, IssueSev.WARNING, "Anonymous", anonymous);

        Map<String, Object> logged =
                firstIssueOf(
                        appendAndRead(List.of(new CheckOutcome("ExampleCheck", List.of(issue))))
                                .getFirst());

        assertEquals("RemediationLogTest", logged.get("fix"));
    }

    @Test
    void logPathIsDerivedFromTheDocumentLineage() {
        assertEquals(
                Path.of("/docs/catalog.autoa11y.log"),
                RemediationLog.resolvePath(Path.of("/docs/catalog_autoa11y.pdf")));
    }

    // == Helpers ==========================================================

    private static Issue resolvedIssue() {
        Issue issue =
                new Issue(
                        IssueType.FIGURE_WITH_TEXT,
                        IssueSev.WARNING,
                        new Locus.AtElem(97, 1, "Figure"),
                        "Figure contains text",
                        new StubFix());
        issue.markResolved(new IssueMsg("Changed Figure to P", Locus.none()));
        return issue;
    }

    private static class StubFix implements IssueFix {
        @Override
        public void apply(DocContext ctx) {}
    }

    private List<String> appendAndRead(List<CheckOutcome> checks) throws IOException {
        Path log = tempDir.resolve("catalog.autoa11y.log");
        RemediationLog.append(
                log, checks, tempDir.resolve("catalog.pdf"), tempDir.resolve("out.pdf"));
        return Files.readAllLines(log);
    }

    /** Parses one JSON line; JSON is a subset of YAML, so SnakeYAML reads it. */
    private static Map<String, Object> parse(String json) {
        return new Yaml().load(json);
    }

    private static Map<String, Object> firstIssueOf(String json) {
        return issues(checks(parse(json)).getFirst()).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> checks(Map<String, Object> run) {
        return (List<Map<String, Object>>) run.get("checks");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> issues(Map<String, Object> check) {
        return (List<Map<String, Object>>) check.get("issues");
    }
}
