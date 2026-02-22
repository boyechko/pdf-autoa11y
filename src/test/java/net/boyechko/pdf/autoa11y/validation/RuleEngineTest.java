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
import net.boyechko.pdf.autoa11y.issues.IssueList;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    @Test
    void rejectsVisitorBeforeItsPrerequisite() {
        var ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RuleEngine(
                                        List.of(),
                                        List.of(DependentVisitor::new, PrereqVisitor::new),
                                        null));
        assertTrue(
                ex.getMessage().contains("PrereqVisitor"),
                "Error should name the missing prerequisite");
    }

    @Test
    void acceptsVisitorAfterItsPrerequisite() {
        assertDoesNotThrow(
                () ->
                        new RuleEngine(
                                List.of(),
                                List.of(PrereqVisitor::new, DependentVisitor::new),
                                null));
    }

    @Test
    void acceptsVisitorWithNoPrerequisites() {
        assertDoesNotThrow(() -> new RuleEngine(List.of(), List.of(PrereqVisitor::new), null));
    }

    // --- Stub visitors for testing ---

    static class PrereqVisitor implements StructureTreeVisitor {
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

    static class DependentVisitor implements StructureTreeVisitor {
        @Override
        public String name() {
            return "Dependent";
        }

        @Override
        public String description() {
            return "";
        }

        @Override
        public Set<Class<? extends StructureTreeVisitor>> prerequisites() {
            return Set.of(PrereqVisitor.class);
        }

        @Override
        public IssueList getIssues() {
            return new IssueList();
        }
    }
}
