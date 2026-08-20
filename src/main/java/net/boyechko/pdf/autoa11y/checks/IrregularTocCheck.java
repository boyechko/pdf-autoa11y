// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import java.util.Set;
import net.boyechko.pdf.autoa11y.fixes.IrregularTocFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects a {@code <TOC>} whose contents were never nested into a TOCI hierarchy — typically a web
 * capture or authoring tool that emitted the table of contents as a flat run of paragraphs, or a
 * list that an earlier pass wrapped as {@code L/LI/LBody}. The schema allows a TOC only {@code
 * Caption}, {@code TOC}, and {@code TOCI} children, so anything else is the defect.
 *
 * <p>The entry hierarchy is recovered from left-edge indentation rather than from link
 * destinations, so TOCs whose entries carry no links are handled the same way.
 *
 * @see IrregularTocFix
 */
public class IrregularTocCheck extends StructTreeCheck {

    private static final Set<String> ALLOWED_CHILD_ROLES = Set.of("Caption", "TOC", "TOCI");

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Irregular TOC Check";
    }

    @Override
    public String description() {
        return "Detects TOC contents not nested into a TOCI hierarchy";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        if (!ctx.hasRole("TOC") || ctx.children().isEmpty()) {
            return true;
        }
        if (ctx.childRoles().stream().allMatch(ALLOWED_CHILD_ROLES::contains)) {
            return true;
        }

        issues.add(
                new Issue(
                        IssueType.IRREGULAR_TOC,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "TOC contents not nested into a TOCI hierarchy",
                        new IrregularTocFix(ctx.node())));

        // The fix rebuilds the whole subtree, so nested irregularities go with it.
        return false;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
