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
package net.boyechko.pdf.autoa11y.issues;

import net.boyechko.pdf.autoa11y.document.DocumentContext;

/** Represents a fix for an accessibility issue found in a PDF document. */
public interface IssueFix {
    int priority();

    void apply(DocumentContext ctx) throws Exception;

    default String describe() {
        return getClass().getSimpleName();
    }

    default String describe(DocumentContext ctx) {
        return describe();
    }

    default String groupLabel() {
        return getClass().getSimpleName();
    }

    default boolean invalidates(IssueFix otherFix) {
        return false;
    }
}
