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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ProcessingReporterTest {
    @Test
    @Tag("visual")
    void rendersVisualTranscriptWithoutRunningRemediation() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ProcessingReporter reporter =
                new ProcessingReporter(new PrintStream(buffer), VerbosityLevel.NORMAL);

        replayProcessingTranscript(reporter);
        reporter.onSuccess("Output saved to output/five_acro_autoa11y_pass1.pdf");

        String rendered = normalize(buffer);

        System.out.println("--- Visual Transcript Preview ---");
        System.out.print(rendered);
        System.out.println("--- End Preview ---");
    }

    private void replayProcessingTranscript(ProcessingReporter reporter) {
        IssueList summaryIssues = new IssueList();

        reporter.onPhaseStart("Document rules");
        reporter.onDetectedSectionStart();
        Issue missingLanguage =
                issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSeverity.ERROR,
                        "Document language (Lang) is not set");
        Issue missingDocumentElement =
                issue(
                        IssueType.MISSING_DOCUMENT_ELEMENT,
                        IssueSeverity.ERROR,
                        "Structure tree root has no Document element");
        reporter.onWarning(missingLanguage.message());
        reporter.onWarning(missingDocumentElement.message());
        reporter.onSuccess("Document tab order is set to follow structure tree");

        reporter.onFixesSectionStart();
        missingLanguage.markResolved("Set document language (Lang)");
        reporter.onIssueFixed(missingLanguage.resolutionNote());

        reporter.onManualReviewSectionStart();
        reporter.onWarning(missingDocumentElement.message());
        summaryIssues.add(missingLanguage);
        summaryIssues.add(missingDocumentElement);

        reporter.onPhaseStart("Needless Nesting Visitor");
        reporter.onDetectedSectionStart();
        IssueList groupedNestingIssues = new IssueList();
        groupedNestingIssues.add(
                issue(
                        IssueType.NEEDLESS_NESTING,
                        IssueSeverity.WARNING,
                        1,
                        "Found needless Part wrapper #311"));
        groupedNestingIssues.add(
                issue(
                        IssueType.NEEDLESS_NESTING,
                        IssueSeverity.WARNING,
                        2,
                        "Found needless Part wrapper #401"));
        groupedNestingIssues.add(
                issue(
                        IssueType.NEEDLESS_NESTING,
                        IssueSeverity.WARNING,
                        3,
                        "Found needless Part wrapper #511"));
        reporter.onIssueGroup(IssueType.NEEDLESS_NESTING.groupLabel(), groupedNestingIssues);

        reporter.onFixesSectionStart();
        for (Issue issue : groupedNestingIssues) {
            issue.markResolved("Flattened needless wrapper");
        }
        reporter.onFixGroup(IssueType.NEEDLESS_NESTING.groupLabel(), groupedNestingIssues);
        summaryIssues.addAll(groupedNestingIssues);

        reporter.onPhaseStart("Schema Validation Visitor");
        reporter.onDetectedSectionStart();
        Issue invalidSchemaTag =
                issue(
                        IssueType.TAG_WRONG_CHILD,
                        IssueSeverity.ERROR,
                        "<Link> not allowed under <Document>");
        reporter.onWarning(invalidSchemaTag.message());
        reporter.onManualReviewSectionStart();
        reporter.onWarning(invalidSchemaTag.message());
        summaryIssues.add(invalidSchemaTag);

        reporter.onSummary(summaryIssues);
    }

    private Issue issue(IssueType type, IssueSeverity severity, String message) {
        return new Issue(type, severity, message);
    }

    private Issue issue(IssueType type, IssueSeverity severity, Integer page, String message) {
        return new Issue(type, severity, new IssueLocation(page, null), message);
    }

    private String normalize(ByteArrayOutputStream buffer) {
        return buffer.toString().replace("\r\n", "\n");
    }
}
