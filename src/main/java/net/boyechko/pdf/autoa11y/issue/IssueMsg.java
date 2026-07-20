// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

/** Message text paired with an optional location for interface-specific rendering. */
public record IssueMsg(String message, IssueLoc where) {}
