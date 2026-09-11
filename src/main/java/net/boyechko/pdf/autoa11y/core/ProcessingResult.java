package net.boyechko.pdf.autoa11y.core;

import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/**
 * Summary of the processing of a PDF document.
 *
 * @param detectedIssues All issues found before processing.
 * @param appliedFixes Issues that were resolved by automated fixes.
 * @param remainingIssues Issues that could not be resolved automatically.
 * @param tempOutputFile The path to the temporary output file.
 * @param remediationLog Per-element record of what each applied fix changed.
 */
public record ProcessingResult(
        IssueList detectedIssues,
        IssueList appliedFixes,
        IssueList remainingIssues,
        Path tempOutputFile,
        boolean dirty,
        List<RemediationEntry> remediationLog) {

    /** Returns an aborted result with no output file and the given fatal issues. */
    public static ProcessingResult aborted(IssueList fatalIssues) {
        return new ProcessingResult(
                fatalIssues, new IssueList(), fatalIssues, null, false, List.of());
    }

    public int issuesDetected() {
        return detectedIssues.size();
    }

    public int issuesResolved() {
        return appliedFixes.size();
    }

    public int issuesRemaining() {
        return remainingIssues.size();
    }
}
