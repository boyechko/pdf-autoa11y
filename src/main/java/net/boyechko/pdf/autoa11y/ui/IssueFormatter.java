// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/** Strategy interface for rendering Issues in different output surfaces. */
public interface IssueFormatter {
    String format(IssueLoc where);

    default String format(IssueMsg msg) {
        return msg.message() + format(msg.where());
    }

    default String formatResolution(Issue issue) {
        IssueMsg resolution = issue.resolution();
        if (resolution == null) {
            throw new IllegalStateException(
                    "Issue has no resolution payload: " + issue.type() + " :: " + issue.message());
        }
        return format(resolution);
    }
}
