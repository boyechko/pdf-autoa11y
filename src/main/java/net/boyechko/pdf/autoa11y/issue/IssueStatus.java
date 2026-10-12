// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

/** Remediation outcome of an issue. */
public enum IssueStatus {
    /** No fix has resolved the issue, including issues with no automatic fix. */
    UNRESOLVED,
    /** The issue's fix ran successfully. */
    RESOLVED,
    /** Another fix covered the issue, so its own fix never ran. */
    SKIPPED,
    /** The issue's fix threw, leaving the issue unresolved. */
    FAILED
}
