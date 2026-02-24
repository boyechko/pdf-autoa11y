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

/** Represents an accessibility issue found in a PDF document. */
public final class Issue {
    private final IssueType type;
    private final IssueSev severity;
    private final IssueLoc where;
    private final String message;
    private final IssueFix fix; // may be null if no automatic fix exists

    private boolean resolved;
    private boolean failed;
    private IssueMsg resolution;

    public Issue(IssueType type, IssueSev sev, String message) {
        this(type, sev, IssueLoc.none(), message, null);
    }

    public Issue(IssueType type, IssueSev sev, String message, IssueFix fix) {
        this(type, sev, IssueLoc.none(), message, fix);
    }

    public Issue(IssueType type, IssueSev sev, IssueLoc where, String message) {
        this(type, sev, where, message, null);
    }

    public Issue(IssueType type, IssueSev sev, IssueLoc where, String message, IssueFix fix) {
        this.type = type;
        this.severity = sev;
        this.where = where;
        this.message = message;
        this.fix = fix;
    }

    public IssueType type() {
        return type;
    }

    public IssueSev severity() {
        return severity;
    }

    public IssueLoc where() {
        return where;
    }

    public String message() {
        return message;
    }

    /** Returns IssueFix if an automatic fix exists; null otherwise. */
    public IssueFix fix() {
        return fix;
    }

    public boolean hasFailed() {
        return failed;
    }

    public boolean isResolved() {
        return resolved;
    }

    public String resolutionNote() {
        return resolution != null ? resolution.message() : null;
    }

    public IssueMsg resolution() {
        return resolution;
    }

    public void markResolved(String note) {
        markResolved(new IssueMsg(note, IssueLoc.none()));
    }

    public void markResolved(IssueMsg resolution) {
        this.resolved = true;
        this.resolution = resolution;
    }

    public void markFailed(String note) {
        markFailed(new IssueMsg(note, IssueLoc.none()));
    }

    public void markFailed(IssueMsg resolution) {
        this.failed = true;
        this.resolution = resolution;
    }
}
