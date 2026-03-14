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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.DocContext;

/** List of accessibility issues found in a PDF document. */
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

    /** Returns a subset of this list that contains only the issues that have been resolved. */
    public IssueList getResolvedIssues() {
        return stream().filter(Issue::isResolved).collect(Collectors.toCollection(IssueList::new));
    }

    /** Returns a subset of this list that contains only the issues that have not been resolved. */
    public IssueList getRemainingIssues() {
        return stream()
                .filter(issue -> !issue.isResolved())
                .collect(Collectors.toCollection(IssueList::new));
    }

    /** Returns true if any issue has FATAL severity, meaning processing cannot continue. */
    public boolean hasFatalIssues() {
        return stream().anyMatch(issue -> issue.severity() == IssueSev.FATAL);
    }

    /** Applies fixes to issues, respecting priority ordering and invalidation. */
    public IssueList applyFixes(DocContext ctx) {
        List<Map.Entry<Issue, IssueFix>> ordered =
                stream()
                        .filter(i -> i.fix() != null)
                        .map(i -> Map.entry(i, i.fix()))
                        .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
                        .toList();

        List<IssueFix> appliedFixes = new ArrayList<>();

        for (Map.Entry<Issue, IssueFix> e : ordered) {
            Issue i = e.getKey();
            IssueFix fx = e.getValue();

            boolean isInvalidated =
                    appliedFixes.stream().anyMatch(applied -> applied.invalidates(fx));

            if (isInvalidated) {
                i.markResolved(new IssueMsg("Skipped: resolved by higher priority fix", i.where()));
                continue;
            }

            try {
                fx.apply(ctx);
                appliedFixes.add(fx);
                i.markResolved(fx.describeLocated(ctx));
            } catch (Exception ex) {
                IssueMsg resolution = fx.describeLocated(ctx);
                i.markFailed(
                        new IssueMsg(
                                resolution.message() + " failed: " + ex.getMessage(),
                                resolution.where()));
            }
        }

        return getResolvedIssues();
    }

    /**
     * Returns a subset of this list that contains only the issues that have failed to be resolved.
     */
    public IssueList getFailedFixes() {
        return stream().filter(Issue::hasFailed).collect(Collectors.toCollection(IssueList::new));
    }
}
