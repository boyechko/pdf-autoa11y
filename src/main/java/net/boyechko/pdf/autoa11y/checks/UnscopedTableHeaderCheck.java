// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.SetHeaderScopeFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Reports TH elements lacking a Table-owned {@code /Scope}, which PDF/UA-1 (7.5) expects of every
 * header cell. Fixes only tables whose headers are unambiguous by position: a first row of THs
 * (scoped Column) above rows of TDs, each row optionally led by a TH (scoped Row).
 */
public class UnscopedTableHeaderCheck extends StructTreeCheck {
    private static final Set<String> VALID_SCOPES = Set.of("Row", "Column", "Both");

    private static final Set<String> ROW_GROUPS = Set.of("THead", "TBody", "TFoot");

    private final IssueList issues = new IssueList();
    private final List<HeaderScope> plannedScopes = new ArrayList<>();

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
        if (visit.hasRole("Table")) {
            plannedScopes.addAll(headerScopesOf(visit.node()));
        } else if (visit.hasRole("TH") && !isScoped(visit.node())) {
            issues.add(
                    new Issue(
                            IssueType.TABLE_HEADER_UNSCOPED,
                            IssueSev.WARNING,
                            visit.locus(),
                            "TH without a Scope attribute",
                            fixFor(visit.node())));
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private static boolean isScoped(PdfStructElem th) {
        DocValue.Scope scope = DocValue.Scope.of(th);
        return scope != null && VALID_SCOPES.contains(scope.value());
    }

    private SetHeaderScopeFix fixFor(PdfStructElem th) {
        return plannedScopes.stream()
                .filter(planned -> StructTree.isSameElement(planned.th(), th))
                .findFirst()
                .map(planned -> new SetHeaderScopeFix(th, planned.scope()))
                .orElse(null);
    }

    /**
     * Column scopes for a first row of THs over rows of TDs, plus Row scopes when every later row
     * is led by a TH; empty for any other layout.
     */
    private static List<HeaderScope> headerScopesOf(PdfStructElem table) {
        List<List<PdfStructElem>> rows =
                rowsOf(table).stream()
                        .map(row -> StructTree.childrenOf(row, PdfStructElem.class))
                        .toList();
        if (rows.size() < 2 || rows.getFirst().isEmpty() || !allHaveRole(rows.getFirst(), "TH")) {
            return List.of();
        }
        List<List<PdfStructElem>> body = rows.subList(1, rows.size());
        List<HeaderScope> scopes = new ArrayList<>();
        rows.getFirst().forEach(th -> scopes.add(new HeaderScope(th, PdfName.Column)));
        if (body.stream().allMatch(row -> allHaveRole(row, "TD"))) {
            return scopes;
        }
        if (body.stream().allMatch(UnscopedTableHeaderCheck::isLedByHeader)) {
            body.forEach(row -> scopes.add(new HeaderScope(row.getFirst(), PdfName.Row)));
            return scopes;
        }
        return List.of();
    }

    private static boolean isLedByHeader(List<PdfStructElem> row) {
        return !row.isEmpty()
                && hasRole(row.getFirst(), "TH")
                && allHaveRole(row.subList(1, row.size()), "TD");
    }

    /** The table's TRs in order, whether direct kids or grouped under THead, TBody, or TFoot. */
    private static List<PdfStructElem> rowsOf(PdfStructElem table) {
        List<PdfStructElem> rows = new ArrayList<>();
        for (PdfStructElem kid : StructTree.childrenOf(table, PdfStructElem.class)) {
            if (hasRole(kid, "TR")) {
                rows.add(kid);
            } else if (ROW_GROUPS.contains(StructTree.mappedRole(kid))) {
                StructTree.childrenOf(kid, PdfStructElem.class).stream()
                        .filter(k -> hasRole(k, "TR"))
                        .forEach(rows::add);
            }
        }
        return rows;
    }

    private static boolean allHaveRole(List<PdfStructElem> cells, String role) {
        return cells.stream().allMatch(cell -> hasRole(cell, role));
    }

    private static boolean hasRole(PdfStructElem elem, String role) {
        return role.equals(StructTree.mappedRole(elem));
    }

    private record HeaderScope(PdfStructElem th, PdfName scope) {}
}
