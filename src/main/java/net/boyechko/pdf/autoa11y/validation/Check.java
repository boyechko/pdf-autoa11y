// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.validation;

import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/// A checker that checks for a specific kind of issues in the PDF document.
public interface Check {
    String name();

    String description();

    String passedMessage();

    String failedMessage();

    IssueList findIssues(DocContext ctx);

    /// Returns checker classes that must run before this checker.
    default Set<Class<? extends Check>> prerequisites() {
        return Set.of();
    }
}
