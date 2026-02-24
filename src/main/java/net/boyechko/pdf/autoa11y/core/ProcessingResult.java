package net.boyechko.pdf.autoa11y.core;

import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/**
 * Summary of the processing of a PDF document.
 *
 * @param originalTagIssues Structure-tree-level issues found before processing.
 * @param appliedTagFixes Structure-tree-level issues that were resolved.
 * @param remainingTagIssues Structure-tree-level issues that were not resolved.
 * @param originalDocumentIssues Document-level issues found before processing.
 * @param appliedDocumentFixes Document-level issues that were resolved.
 * @param remainingDocumentIssues Document-level issues that were not resolved.
 * @param tempOutputFile The path to the temporary output file.
 */
public record ProcessingResult(
        IssueList originalTagIssues,
        IssueList appliedTagFixes,
        IssueList remainingTagIssues,
        IssueList originalDocumentIssues,
        IssueList appliedDocumentFixes,
        IssueList remainingDocumentIssues,
        Path tempOutputFile) {

    /** Returns an aborted result with no output file and the given fatal issues. */
    public static ProcessingResult aborted(IssueList fatalIssues) {
        return new ProcessingResult(
                new IssueList(),
                new IssueList(),
                new IssueList(),
                fatalIssues,
                new IssueList(),
                fatalIssues,
                null);
    }

    public int totalIssuesDetected() {
        return originalTagIssues.size() + originalDocumentIssues.size();
    }

    public int totalIssuesResolved() {
        return appliedTagFixes.size() + appliedDocumentFixes.size();
    }

    public int totalIssuesRemaining() {
        return remainingTagIssues.size() + remainingDocumentIssues.size();
    }

    public boolean hasTagIssues() {
        return !remainingTagIssues.isEmpty();
    }

    public boolean hasDocumentIssues() {
        return !remainingDocumentIssues.isEmpty();
    }
}
