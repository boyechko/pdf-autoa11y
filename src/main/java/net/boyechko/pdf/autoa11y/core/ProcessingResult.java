package net.boyechko.pdf.autoa11y.core;

import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSnapshot;

/**
 * Summary of the processing of a PDF document.
 *
 * @param detectedIssues All issues detected across the pipeline's checks, with their final
 *     outcomes.
 * @param appliedFixes Resolved issues, including those skipped as covered by another fix.
 * @param remainingIssues Issues that could not be resolved automatically.
 * @param tempOutputFile The path to the temporary output file.
 * @param remediationLog Issue snapshots captured after successful fix applications, in execution
 *     order; excludes skipped, failed, and unresolved issues.
 */
public record ProcessingResult(
        IssueList detectedIssues,
        IssueList appliedFixes,
        IssueList remainingIssues,
        Path tempOutputFile,
        boolean dirty,
        List<IssueSnapshot> remediationLog) {

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
