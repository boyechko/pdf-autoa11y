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

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class IssueList extends ArrayList<Issue> {

    public IssueList() {
        super();
    }

    public IssueList(Collection<Issue> issues) {
        super(issues != null ? issues : new ArrayList<>());
    }

    public IssueList(Issue issue) {
        super();
        if (issue != null) {
            add(issue);
        }
    }

    // Domain-specific convenience methods
    public IssueList getResolvedIssues() {
        return stream().filter(Issue::isResolved).collect(Collectors.toCollection(IssueList::new));
    }

    public IssueList getRemainingIssues() {
        return stream()
                .filter(issue -> !issue.isResolved())
                .collect(Collectors.toCollection(IssueList::new));
    }

    public IssueList getFailedFixes() {
        return stream().filter(Issue::hasFailed).collect(Collectors.toCollection(IssueList::new));
    }
}
