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

import java.util.Set;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/** Visitor interface for PDF structure tree traversal. */
public interface StructTreeChecker {

    String name();

    String description();

    default boolean enterElement(VisitorContext ctx) {
        return true;
    }

    default void leaveElement(VisitorContext ctx) {}

    default void beforeTraversal(VisitorContext ctx) {}

    default void afterTraversal() {}

    IssueList getIssues();

    /**
     * Returns visitor classes that must run before this visitor. The pipeline validates at
     * construction time that all prerequisites appear earlier in the visitor list.
     */
    default Set<Class<? extends StructTreeChecker>> prerequisites() {
        return Set.of();
    }
}
