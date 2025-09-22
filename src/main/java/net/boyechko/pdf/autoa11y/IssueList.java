package net.boyechko.pdf.autoa11y;

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
        return stream()
            .filter(Issue::isResolved)
            .collect(Collectors.toCollection(IssueList::new));
    }

    public IssueList getRemainingIssues() {
        return stream()
            .filter(issue -> !issue.isResolved())
            .collect(Collectors.toCollection(IssueList::new));
    }

    public IssueList getFailedFixes() {
        return stream()
            .filter(Issue::hasFailed)
            .collect(Collectors.toCollection(IssueList::new));
    }
}
