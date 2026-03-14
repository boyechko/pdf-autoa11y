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
package net.boyechko.pdf.autoa11y.issue;

import static org.junit.jupiter.api.Assertions.*;

import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class IssueListTest {

    @Test
    void applyFixesResolvesAllFixableIssues() {
        IssueList issues = new IssueList();

        Issue lowPriority =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        IssueLoc.none(),
                        "low",
                        new StubFix(200));
        Issue highPriority =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        IssueLoc.none(),
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
                        IssueLoc.none(),
                        "kept",
                        new InvalidatingFix(100));
        Issue skipped =
                new Issue(
                        IssueType.LANGUAGE_NOT_SET,
                        IssueSev.WARNING,
                        IssueLoc.none(),
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
