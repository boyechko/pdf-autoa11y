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
package net.boyechko.pdf.autoa11y;

public interface IssueFix {
    /** Lower numbers run earlier. Keep it simple: 0..100 is plenty. */
    int priority();

    /** Idempotent application of the fix. */
    void apply(DocumentContext ctx) throws Exception;

    /** Human-readable for logs/UI. */
    default String describe() {
        return getClass().getSimpleName();
    }

    /** Human-readable for logs/UI with document context for page numbers. */
    default String describe(DocumentContext ctx) {
        return describe();
    }

    /**
     * Check if this fix invalidates another fix. Called after this fix is applied to determine if
     * other pending fixes should be skipped.
     *
     * @param otherFix The other fix to check for invalidation
     * @return true if the other fix should be skipped, false otherwise
     */
    default boolean invalidates(IssueFix otherFix) {
        return false;
    }
}
