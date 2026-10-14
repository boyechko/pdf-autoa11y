// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import org.junit.jupiter.api.Test;

class IssueListTest {

    @Test
    void applyFixesResolvesAllFixableIssues() {
        IssueList issues = new IssueList();

        Issue lowPriority =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        Locus.none(),
                        "low",
                        new StubFix(200));
        Issue highPriority =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        Locus.none(),
                        "high",
                        new StubFix(100));
        issues.add(lowPriority);
        issues.add(highPriority);

        IssueList resolved = issues.applyFixes(null);

        assertEquals(2, resolved.size());
        assertTrue(highPriority.isResolved());
        assertTrue(lowPriority.isResolved());
    }

    @Test
    void applyFixesSkipsInvalidatedFixes() {
        IssueList issues = new IssueList();

        Issue kept =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        Locus.none(),
                        "kept",
                        new InvalidatingFix(100));
        Issue skipped =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        Locus.none(),
                        "skipped",
                        new StubFix(200));
        issues.add(kept);
        issues.add(skipped);

        IssueList resolved = issues.applyFixes(null);

        assertEquals(2, resolved.size());
        assertTrue(kept.isResolved());
        assertTrue(skipped.isResolved());
        assertTrue(
                skipped.resolution().message().contains("Skipped"),
                "Invalidated fix should be marked as skipped");
    }

    @Test
    void applyFixesContinuesAfterFailedFix() {
        Issue failed =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        "failed",
                        ctx -> {
                            throw new IllegalStateException();
                        });
        Issue unfixable = new Issue(IssueType.LANGUAGE_NOT_SET, IssueSev.WARNING, "unfixable");
        Issue successful =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        "successful",
                        new StubFix(10));
        IssueList issues = new IssueList(List.of(failed, unfixable, successful));
        issues.applyFixes(null);

        assertTrue(failed.hasFailed());
        assertFalse(failed.isResolved());
        assertFalse(unfixable.isResolved());
        assertTrue(successful.isResolved());
    }

    // --- Stub fixes for testing ---

    static class StubFix implements IssueFix {
        private final int priority;

        StubFix(int priority) {
            this.priority = priority;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public void apply(DocContext ctx) {}
    }

    /** A fix that invalidates all other fixes. */
    static class InvalidatingFix extends StubFix {
        InvalidatingFix(int priority) {
            super(priority);
        }

        @Override
        public boolean invalidates(IssueFix otherFix) {
            return true;
        }
    }
}
