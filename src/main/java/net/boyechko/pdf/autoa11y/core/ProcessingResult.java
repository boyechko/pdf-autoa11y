package net.boyechko.pdf.autoa11y.core;

import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/**
 * Summary of the processing of a PDF document.
 *
 * @param detectedIssues All issues found before processing.
 * @param appliedFixes Issues that were resolved by automated fixes.
 * @param remainingIssues Issues that could not be resolved automatically.
 * @param tempOutputFile The path to the temporary output file.
 */
public record ProcessingResult(
        IssueList detectedIssues,
        IssueList appliedFixes,
        IssueList remainingIssues,
        Path tempOutputFile) {

    /** Returns an aborted result with no output file and the given fatal issues. */
    public static ProcessingResult aborted(IssueList fatalIssues) {
        return new ProcessingResult(fatalIssues, new IssueList(), fatalIssues, null);
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
