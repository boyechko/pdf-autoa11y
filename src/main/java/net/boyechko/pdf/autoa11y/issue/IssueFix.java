/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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

import net.boyechko.pdf.autoa11y.document.DocContext;

/** Represents a fix for an accessibility issue found in a PDF document. */
public interface IssueFix {
    /**
     * Orders fixes within a single check's {@link IssueList#applyFixes} pass (lower runs first;
     * ties keep emission order). Each check runs on its own copy of the PDF, so priorities never
     * interact across checks. Override only when one check emits multiple fix classes whose apply
     * order matters, as MistaggedListCheck's fixes do.
     */
    default int priority() {
        return 0;
    }

    void apply(DocContext ctx) throws Exception;

    default String describe() {
        return getClass().getSimpleName();
    }

    default String describe(DocContext ctx) {
        return describe();
    }

    default IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), IssueLoc.none());
    }

    default String groupLabel() {
        return getClass().getSimpleName();
    }

    default boolean invalidates(IssueFix otherFix) {
        return false;
    }

    /**
     * Returns how many items this fix resolved for report group headlines.
     *
     * <p>A non-positive value means "use the default issue count".
     */
    default int resolvedItemCount() {
        return 0;
    }
}
