// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.DocValue.Scribble;
import net.boyechko.pdf.autoa11y.document.Tag;
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
 *
 * <p>A table it leaves unfixed is stamped with a finding led by its object number, as in {@code
 * 17928 // ?unscoped headers}, and each of its rows with its cell roles, as in {@code cells
 * TH,TDx3}, so the table and the layout that defeated the fix show in Acrobat's Tags pane.
 */
public class UnscopedTableHeaderCheck extends StructTreeCheck {
    private static final Set<String> VALID_SCOPES = Set.of("Row", "Column", "Both");

    private static final Set<String> ROW_GROUPS = Set.of("THead", "TBody", "TFoot");

    /** Heads the row-shape segment, so it can be replaced without matching the rest of its text. */
    private static final String SHAPE_HEAD = "cells";

    /** Heads the finding stamped on a table left unfixed. */
    private static final String FINDING_HEAD = Scribble.FINDING_MARK + "unscoped";

    private static final String FINDING = FINDING_HEAD + " headers";

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
            List<Tag> rows = rowsOf(visit.tag());
            List<HeaderScope> scopes = headerScopesOf(rows);
            plannedScopes.addAll(scopes);
            boolean unfixed = scopes.isEmpty() && hasUnscopedHeader(rows);
            refreshFinding(visit.tag(), unfixed);
            refreshRowShapes(rows, unfixed);
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

    /**
     * Stamps the finding on a table left unfixed, led by its object number, and drops both once the
     * table is fixable.
     */
    private void refreshFinding(Tag table, boolean unfixed) {
        String before = table.scribble().rawValue();
        table.unstamp(FINDING_HEAD);
        if (unfixed) {
            table.stamp(FINDING);
            table.stampObjNum();
        } else if (table.scribble().segments().equals(List.of(Integer.toString(table.objNum())))) {
            table.scribble(Scribble.EMPTY);
        }
        if (!table.scribble().rawValue().equals(before)) {
            markDocChanged();
        }
    }

    /** Stamps each row's shape on it while the table is left unfixed, and drops it otherwise. */
    private void refreshRowShapes(List<Tag> rows, boolean unfixed) {
        for (Tag row : rows) {
            String before = row.scribble().rawValue();
            row.unstamp(SHAPE_HEAD);
            if (unfixed && !row.children().isEmpty()) {
                row.stamp(SHAPE_HEAD + " " + shapeOf(row));
            }
            if (!row.scribble().rawValue().equals(before)) {
                markDocChanged();
            }
        }
    }

    /** The row's cell roles in order, a run of one role collapsed as in {@code TH,TDx3}. */
    private static String shapeOf(Tag row) {
        List<String> runs = new ArrayList<>();
        String role = null;
        int count = 0;
        for (Tag cell : row.children()) {
            if (cell.role().equals(role)) {
                count++;
                continue;
            }
            if (role != null) runs.add(run(role, count));
            role = cell.role();
            count = 1;
        }
        runs.add(run(role, count));
        return String.join(",", runs);
    }

    private static String run(String role, int count) {
        return count == 1 ? role : role + "x" + count;
    }

    private static boolean hasUnscopedHeader(List<Tag> rows) {
        return rows.stream()
                .flatMap(row -> row.children().stream())
                .anyMatch(cell -> hasRole(cell, "TH") && !isScoped(cell.elem()));
    }

    private static boolean isScoped(PdfStructElem th) {
        DocValue.Scope scope = DocValue.Scope.of(th);
        return scope != null && VALID_SCOPES.contains(scope.value());
    }

    private SetHeaderScopeFix fixFor(PdfStructElem th) {
        return plannedScopes.stream()
                .filter(planned -> Tag.of(planned.th()).equals(Tag.of(th)))
                .findFirst()
                .map(planned -> new SetHeaderScopeFix(th, planned.scope()))
                .orElse(null);
    }

    /**
     * Column scopes for a first row of THs over rows of TDs, plus Row scopes when every later row
     * is led by a TH; empty for any other layout.
     */
    private static List<HeaderScope> headerScopesOf(List<Tag> tableRows) {
        List<List<Tag>> rows = tableRows.stream().map(Tag::children).toList();
        if (rows.size() < 2 || rows.getFirst().isEmpty() || !allHaveRole(rows.getFirst(), "TH")) {
            return List.of();
        }
        List<List<Tag>> body = rows.subList(1, rows.size());
        List<HeaderScope> scopes = new ArrayList<>();
        rows.getFirst().forEach(th -> scopes.add(new HeaderScope(th.elem(), PdfName.Column)));
        if (body.stream().allMatch(row -> allHaveRole(row, "TD"))) {
            return scopes;
        }
        if (body.stream().allMatch(UnscopedTableHeaderCheck::isLedByHeader)) {
            body.forEach(row -> scopes.add(new HeaderScope(row.getFirst().elem(), PdfName.Row)));
            return scopes;
        }
        return List.of();
    }

    private static boolean isLedByHeader(List<Tag> row) {
        return !row.isEmpty()
                && hasRole(row.getFirst(), "TH")
                && allHaveRole(row.subList(1, row.size()), "TD");
    }

    /** The table's TRs in order, whether direct kids or grouped under THead, TBody, or TFoot. */
    private static List<Tag> rowsOf(Tag table) {
        List<Tag> rows = new ArrayList<>();
        for (Tag kid : table.children()) {
            if (hasRole(kid, "TR")) {
                rows.add(kid);
            } else if (ROW_GROUPS.contains(kid.role())) {
                kid.children().stream().filter(k -> hasRole(k, "TR")).forEach(rows::add);
            }
        }
        return rows;
    }

    private static boolean allHaveRole(List<Tag> cells, String role) {
        return cells.stream().allMatch(cell -> hasRole(cell, role));
    }

    private static boolean hasRole(Tag tag, String role) {
        return role.equals(tag.role());
    }

    private record HeaderScope(PdfStructElem th, PdfName scope) {}
}
