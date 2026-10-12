// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IssueTest {

    private static final IssueMsg OUTCOME = new IssueMsg("outcome", IssueLoc.none());

    @Test
    void newIssueIsUnresolved() {
        Issue issue = newIssue();

        assertEquals(IssueStatus.UNRESOLVED, issue.status());
        assertFalse(issue.isResolved());
    }

    @Test
    void markResolvedMakesIssueResolved() {
        Issue issue = newIssue();

        issue.markResolved(OUTCOME);

        assertEquals(IssueStatus.RESOLVED, issue.status());
        assertTrue(issue.isResolved());
        assertFalse(issue.isSkipped());
    }

    @Test
    void skippedIssueStillCountsAsResolved() {
        Issue issue = newIssue();

        issue.markSkipped(OUTCOME);

        assertEquals(IssueStatus.SKIPPED, issue.status());
        assertTrue(issue.isSkipped());
        assertTrue(issue.isResolved());
    }

    @Test
    void failedIssueIsNotResolved() {
        Issue issue = newIssue();

        issue.markFailed(OUTCOME);

        assertEquals(IssueStatus.FAILED, issue.status());
        assertTrue(issue.hasFailed());
        assertFalse(issue.isResolved());
    }

    private static Issue newIssue() {
        return new Issue(IssueType.LANGUAGE_NOT_SET, IssueSev.WARNING, "message");
    }
}
