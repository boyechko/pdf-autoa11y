// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.fixes.StaleScribbleFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Flags structure elements whose /T (title) value starts with StructTree.SCRIBBLE_PREFIX,
 * indicating a workflow scribble left over from manual remediation that should be cleared before
 * final output.
 *
 * <p>Which scribbles count is governed by {@link Scope}, configurable per document through the
 * sidecar's {@code StaleScribbleCheck: scope:} key.
 */
public class StaleScribbleCheck extends StructTreeCheck {

    /** Which scribbles the check treats as stale. */
    public enum Scope {
        /** Every scribble, whoever wrote it. Suited to a final-output cleanup pass. */
        ALL,
        /** Only scribbles carrying the tool-authorship mark, leaving the user's notes intact. */
        TOOL_AUTHORED;

        /** Parses a sidecar scope value, case-insensitively; a null or blank value means ALL. */
        public static Scope parse(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown scope '" + value + "'; valid values are " + names());
            }
        }

        /** Returns the legal scope values, for use in error messages. */
        public static String names() {
            return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
        }
    }

    private final IssueList issues = new IssueList();
    private final Scope scope;

    public StaleScribbleCheck() {
        this(Scope.ALL);
    }

    public StaleScribbleCheck(Scope scope) {
        this.scope = scope;
    }

    @Override
    public String name() {
        return "Stale Scribble Check";
    }

    @Override
    public String description() {
        return scope == Scope.TOOL_AUTHORED
                ? "Tool-authored annotations in /T should be cleared before final output"
                : "Workflow annotations in /T should be cleared before final output";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        DocValue.Scribble scribble = DocValue.Scribble.of(ctx.node());
        if (scribble == null || !inScope(scribble)) {
            return true;
        }
        issues.add(
                new Issue(
                        IssueType.STALE_SCRIBBLE,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "Stale workflow scribble: " + scribble.rawValue(),
                        new StaleScribbleFix(ctx.node(), scribble.rawValue())));
        return true;
    }

    /** Whether this check's scope covers the given scribble. */
    private boolean inScope(DocValue.Scribble scribble) {
        return scope != Scope.TOOL_AUTHORED || scribble.toolAuthored();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
