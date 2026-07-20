// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.validation.Check;

public class NoOpProcessingListener implements ProcessingListener {
    @Override
    public void onCheckStart(Check check) {}

    @Override
    public void onSuccess(String message) {}

    @Override
    public void onWarning(Issue issue) {}

    @Override
    public void onIssueFixed(Issue issue) {}

    @Override
    public void onSummary(IssueList allIssues) {}
}
