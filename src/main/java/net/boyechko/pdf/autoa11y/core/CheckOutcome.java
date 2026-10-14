// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import java.util.List;
import net.boyechko.pdf.autoa11y.issue.Issue;

/**
 * A check's detected issues and their outcomes within one run.
 *
 * @param check check class name
 * @param issues issues in detection order, each carrying its final status; empty when the check
 *     found no issues
 */
public record CheckOutcome(String check, List<Issue> issues) {
    public CheckOutcome {
        issues = List.copyOf(issues);
    }
}
