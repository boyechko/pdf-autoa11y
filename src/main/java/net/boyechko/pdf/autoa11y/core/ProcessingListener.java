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
package net.boyechko.pdf.autoa11y.core;

import java.util.List;
import net.boyechko.pdf.autoa11y.issues.Issue;

public interface ProcessingListener {
    void onPhaseStart(String phaseName);

    void onSuccess(String message);

    void onWarning(String message);

    void onIssueFixed(String resolutionNote);

    void onSummary(int detected, int resolved, int remaining);

    default void onError(String message) {}

    default void onInfo(String message) {}

    default void onVerboseOutput(String message) {}

    default void onSubsection(String header) {}

    default void onIssueGroup(String groupLabel, List<Issue> issues) {
        for (Issue issue : issues) {
            onWarning(issue.message());
        }
    }

    default void onFixGroup(String groupLabel, List<Issue> resolvedIssues) {
        for (Issue issue : resolvedIssues) {
            if (issue.isResolved()) {
                onIssueFixed(issue.resolutionNote());
            }
        }
    }
}
