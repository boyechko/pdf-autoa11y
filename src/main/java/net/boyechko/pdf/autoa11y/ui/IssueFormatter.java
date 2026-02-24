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
        if (resolution != null) {
            return format(resolution);
        }
        return issue.resolutionNote() != null ? issue.resolutionNote() : issue.message();
    }
}
