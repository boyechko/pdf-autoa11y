// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_PREFIX;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
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
 */
public class StaleScribbleCheck extends StructTreeCheck {
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Stale Scribble Check";
    }

    @Override
    public String description() {
        return "Workflow annotations in /T should be cleared before final output";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        PdfString title = ctx.node().getPdfObject().getAsString(PdfName.T);
        if (title == null) {
            return true;
        }
        String value = title.toUnicodeString();
        if (value.startsWith(SCRIBBLE_PREFIX)) {
            issues.add(
                    new Issue(
                            IssueType.STALE_SCRIBBLE,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Stale workflow scribble: " + value,
                            new StaleScribbleFix(ctx.node(), value)));
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
