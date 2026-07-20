// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import java.util.List;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.validation.Check;

/// Interface for reporting progress and results of the processing.
public interface ProcessingListener {
    void onCheckStart(Check check);

    void onSuccess(String message);

    void onWarning(Issue issue);

    void onIssueFixed(Issue issue);

    void onSummary(IssueList allIssues);

    default void onError(String message) {}

    default void onInfo(String message) {}

    default void onVerboseOutput(String message) {}

    default void onSubsection(String header) {}

    default void onDetectedSectionStart() {
        onSubsection("Detected issues");
    }

    default void onFixesSectionStart() {
        onSubsection("Fixes applied");
    }

    default void onManualReviewSectionStart() {
        onSubsection("Needs manual review");
    }

    default void onIssueGroup(String groupLabel, List<Issue> issues) {
        for (Issue issue : issues) {
            onWarning(issue);
        }
    }

    default void onFixGroup(String groupLabel, List<Issue> resolvedIssues) {
        for (Issue issue : resolvedIssues) {
            if (issue.isResolved()) {
                onIssueFixed(issue);
            }
        }
    }
}
