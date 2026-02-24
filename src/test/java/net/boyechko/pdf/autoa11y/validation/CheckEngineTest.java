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
package net.boyechko.pdf.autoa11y.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.junit.jupiter.api.Test;

class CheckEngineTest {

    @Test
    void rejectsVisitorBeforeItsPrerequisite() {
        var ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new CheckEngine(
                                        List.of(),
                                        List.of(DependentCheck::new, PrereqCheck::new),
                                        null));
        assertTrue(
                ex.getMessage().contains("PrereqCheck"),
                "Error should name the missing prerequisite");
    }

    @Test
    void acceptsVisitorAfterItsPrerequisite() {
        assertDoesNotThrow(
                () ->
                        new CheckEngine(
                                List.of(), List.of(PrereqCheck::new, DependentCheck::new), null));
    }

    @Test
    void acceptsVisitorWithNoPrerequisites() {
        assertDoesNotThrow(() -> new CheckEngine(List.of(), List.of(PrereqCheck::new), null));
    }

    // --- Stub visitors for testing ---

    static class PrereqCheck extends StructTreeCheck {
        @Override
        public String name() {
            return "Prereq";
        }

        @Override
        public String description() {
            return "";
        }

        @Override
        public IssueList getIssues() {
            return new IssueList();
        }
    }

    static class DependentCheck extends StructTreeCheck {
        @Override
        public String name() {
            return "Dependent";
        }

        @Override
        public String description() {
            return "";
        }

        @Override
        public Set<Class<? extends StructTreeCheck>> prerequisites() {
            return Set.of(PrereqCheck.class);
        }

        @Override
        public IssueList getIssues() {
            return new IssueList();
        }
    }
}
