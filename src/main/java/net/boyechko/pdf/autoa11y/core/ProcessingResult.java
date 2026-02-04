package net.boyechko.pdf.autoa11y.core;

import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.issues.IssueList;

/** Result of the processing of a PDF document. */
public record ProcessingResult(
        IssueList originalTagIssues,
        IssueList appliedTagFixes,
        IssueList remainingTagIssues,
        IssueList originalDocumentIssues,
        IssueList appliedDocumentFixes,
        IssueList remainingDocumentIssues,
        Path tempOutputFile) {

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
