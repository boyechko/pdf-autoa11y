// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Reports TH elements lacking a Table-owned {@code /Scope}, which PDF/UA-1 (7.5) expects of every
 * header cell.
 */
public class UnscopedTableHeaderCheck extends StructTreeCheck {
    private static final Set<String> VALID_SCOPES = Set.of("Row", "Column", "Both");

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Unscoped Table Header Check";
    }

    @Override
    public String description() {
        return "Table header cells should have a Scope attribute";
    }

    @Override
    public boolean enterElement(Visit visit) {
        if (!visit.hasRole("TH")) {
            return true;
        }
        DocValue.Scope scope = DocValue.Scope.of(visit.node());
        if (scope == null || !VALID_SCOPES.contains(scope.value())) {
            issues.add(
                    new Issue(
                            IssueType.TABLE_HEADER_UNSCOPED,
                            IssueSev.WARNING,
                            visit.locus(),
                            "TH without a Scope attribute"));
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
