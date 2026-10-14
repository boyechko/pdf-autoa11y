package net.boyechko.pdf.autoa11y.core;

import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/**
 * Summary of the processing of a PDF document.
 *
 * @param detectedIssues All issues detected across the pipeline's checks, with their final
 *     outcomes.
 * @param appliedFixes Resolved issues, including those skipped as covered by another fix.
 * @param remainingIssues Issues that could not be resolved automatically.
 * @param tempOutputFile The path to the temporary output file.
 * @param checkOutcomes Per-check outcomes in pipeline order, including checks that found no issues;
 *     each check's issues remain in detection order.
 */
public record ProcessingResult(
        IssueList detectedIssues,
        IssueList appliedFixes,
        IssueList remainingIssues,
        Path tempOutputFile,
        boolean dirty,
        List<CheckOutcome> checkOutcomes) {

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
