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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Flags structure elements whose /T (title) value starts with "__", indicating a workflow
 * annotation left over from manual remediation that should be cleared before final output.
 */
public class StaleAnnotationCheck extends StructTreeCheck {
    static final String ANNOTATION_PREFIX = "__";

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Stale Annotation Check";
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
        if (value.startsWith(ANNOTATION_PREFIX)) {
            issues.add(
                    new Issue(
                            IssueType.STALE_ANNOTATION,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Stale workflow annotation: " + value));
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
