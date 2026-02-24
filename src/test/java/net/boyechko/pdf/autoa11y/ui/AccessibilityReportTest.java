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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessibilityReportTest {

    @TempDir Path tempDir;

    @Test
    void noIssuesProducesCleanReport() throws IOException {
        ProcessingResult result = resultWith(new IssueList(), new IssueList());
        String report = generateReport(result);

        assertContains(report, "Issues detected:  0");
        assertContains(report, "Issues resolved:  0");
        assertContains(report, "Issues remaining: 0");
        assertContains(report, "No accessibility issues were detected.");
        assertNotContains(report, "[RESOLVED]");
        assertNotContains(report, "[REMAINING]");
    }

    @Test
    void resolvedIssuesShowResolutionNotes() throws IOException {
        IssueList docIssues = new IssueList();
        Issue langIssue =
                new Issue(IssueType.LANGUAGE_NOT_SET, IssueSev.ERROR, "Document language not set");
        langIssue.markResolved(new IssueMsg("Set document language to \"en\"", langIssue.where()));
        docIssues.add(langIssue);

        ProcessingResult result = resultWith(docIssues, new IssueList());
        String report = generateReport(result);

        assertContains(report, "[RESOLVED] 1 language not set");
        assertContains(report, "  -> Set document language to \"en\"");
        assertContains(report, "Issues detected:  1");
        assertContains(report, "Issues resolved:  1");
        assertContains(report, "Issues remaining: 0");
    }

    @Test
    void remainingIssuesShowMessagesWithLocation() throws IOException {
        IssueList tagIssues = new IssueList();
        tagIssues.add(
                new Issue(
                        IssueType.FIGURE_MISSING_ALT,
                        IssueSev.ERROR,
                        IssueLoc.atPage(3),
                        "Figure missing alt text"));
        tagIssues.add(
                new Issue(
                        IssueType.FIGURE_MISSING_ALT,
                        IssueSev.ERROR,
                        IssueLoc.atPage(7),
                        "Figure missing alt text"));

        ProcessingResult result = resultWith(new IssueList(), tagIssues);
        String report = generateReport(result);

        assertContains(report, "[REMAINING] 2 images missing alt text");
        assertContains(report, "  - Figure missing alt text (p. 3)");
        assertContains(report, "  - Figure missing alt text (p. 7)");
        assertContains(report, "Issues remaining: 2");
    }

    @Test
    void mixedResolvedAndRemainingGroupedCorrectly() throws IOException {
        IssueList tagIssues = new IssueList();

        Issue resolved =
                new Issue(IssueType.TAG_WRONG_CHILD, IssueSev.WARNING, "Wrong child in Document");
        resolved.markResolved(new IssueMsg("Wrapped in Sect", resolved.where()));
        tagIssues.add(resolved);

        Issue remaining =
                new Issue(IssueType.TAG_WRONG_CHILD, IssueSev.WARNING, "Wrong child in Table");
        tagIssues.add(remaining);

        ProcessingResult result = resultWith(new IssueList(), tagIssues);
        String report = generateReport(result);

        assertContains(report, "[RESOLVED] 1 tags with wrong children");
        assertContains(report, "  -> Wrapped in Sect");
        assertContains(report, "[REMAINING] 1 tags with wrong children");
        assertContains(report, "  - Wrong child in Table");
    }

    @Test
    void headerContainsInputAndOutputPaths() throws IOException {
        ProcessingResult result = resultWith(new IssueList(), new IssueList());
        String report = generateReport(result);

        assertContains(report, "PDF Accessibility Remediation Report");
        assertContains(report, "Input:    /input/test.pdf");
        assertContains(report, "Output:   /output/test_autoa11y.pdf");
        assertContains(report, "Date:");
    }

    @Test
    void documentAndTagSectionsAppearSeparately() throws IOException {
        IssueList docIssues = new IssueList();
        docIssues.add(new Issue(IssueType.LANGUAGE_NOT_SET, IssueSev.ERROR, "Language not set"));

        IssueList tagIssues = new IssueList();
        tagIssues.add(new Issue(IssueType.EMPTY_ELEMENT, IssueSev.WARNING, "Empty element found"));

        ProcessingResult result = resultWith(docIssues, tagIssues);
        String report = generateReport(result);

        assertContains(report, "Document-Level Issues");
        assertContains(report, "Structure Tree Issues");
        // Document section comes before tag section
        assertTrue(
                report.indexOf("Document-Level Issues") < report.indexOf("Structure Tree Issues"),
                "Document-Level Issues should appear before Structure Tree Issues");
    }

    @Test
    void resolvedGroupsUseFixResolvedItemCount() throws IOException {
        IssueList tagIssues = new IssueList();

        Issue needlessNesting =
                new Issue(
                        IssueType.NEEDLESS_NESTING,
                        IssueSev.WARNING,
                        "Found grouping wrappers",
                        new FixedCountIssueFix(49));
        needlessNesting.markResolved(
                new IssueMsg("Flattened 49 grouping element(s)", IssueLoc.none()));
        tagIssues.add(needlessNesting);

        Issue pageParts =
                new Issue(
                        IssueType.PAGE_PARTS_NOT_NORMALIZED,
                        IssueSev.WARNING,
                        "Direct Document children should be grouped into page-level Part elements",
                        new FixedCountIssueFix(9675));
        pageParts.markResolved(
                new IssueMsg(
                        "Normalized page Parts: created 653 Part(s), moved 9675 element(s)",
                        IssueLoc.none()));
        tagIssues.add(pageParts);

        ProcessingResult result = resultWith(new IssueList(), tagIssues);
        String report = generateReport(result);

        assertContains(report, "[RESOLVED] 49 unnecessary Part/Sect/Art/Div wrappers");
        assertContains(report, "[RESOLVED] 9675 elements not grouped into page-level Part elements");
    }

    private ProcessingResult resultWith(IssueList docIssues, IssueList tagIssues) {
        IssueList allIssues = new IssueList();
        allIssues.addAll(docIssues);
        allIssues.addAll(tagIssues);

        return new ProcessingResult(
                tagIssues,
                tagIssues.getResolvedIssues(),
                tagIssues.getRemainingIssues(),
                docIssues,
                docIssues.getResolvedIssues(),
                docIssues.getRemainingIssues(),
                null);
    }

    private String generateReport(ProcessingResult result) throws IOException {
        Path reportPath = tempDir.resolve("test.report.txt");
        AccessibilityReport.write(
                result,
                Path.of("/input/test.pdf"),
                Path.of("/output/test_autoa11y.pdf"),
                reportPath);
        return Files.readString(reportPath);
    }

    private void assertContains(String text, String expected) {
        assertTrue(
                text.contains(expected),
                "Expected report to contain: \"" + expected + "\"\nActual:\n" + text);
    }

    private void assertNotContains(String text, String unexpected) {
        assertFalse(
                text.contains(unexpected),
                "Expected report NOT to contain: \"" + unexpected + "\"\nActual:\n" + text);
    }

    private static final class FixedCountIssueFix implements IssueFix {
        private final int resolvedItemCount;

        private FixedCountIssueFix(int resolvedItemCount) {
            this.resolvedItemCount = resolvedItemCount;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public void apply(DocContext ctx) {
            // No-op for reporting tests.
        }

        @Override
        public int resolvedItemCount() {
            return resolvedItemCount;
        }
    }
}
