package net.boyechko.pdf.autoa11y;

import java.util.List;

public class ProcessingResult {
    private final List<Issue> detectedIssues;

    public ProcessingResult(List<Issue> detectedIssues) {
        this.detectedIssues = detectedIssues != null ? List.copyOf(detectedIssues) : List.of();
    }

    // Core data
    public List<Issue> getDetectedIssues() { return detectedIssues; }

    // Derived convenience methods for UI layers
    public List<Issue> getResolvedIssues() {
        return detectedIssues.stream()
            .filter(Issue::isResolved)
            .toList();
    }

    public List<Issue> getRemainingIssues() {
        return detectedIssues.stream()
            .filter(issue -> !issue.isResolved())
            .toList();
    }
}
