/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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

import static net.boyechko.pdf.autoa11y.document.StructTree.SCRIBBLE_PREFIX;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import net.boyechko.pdf.autoa11y.fixes.MistaggedArtifactFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Artifacts elements whose /T scribble is "__artifact", indicating manual remediation flagged them
 * as decorative content that should not appear in the tag tree.
 */
public class ArtifactFlaggedElementsCheck extends StructTreeCheck {
    private static final String ARTIFACT_SCRIBBLE = SCRIBBLE_PREFIX + "artifact";

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Artifact-Flagged Elements Check";
    }

    @Override
    public String description() {
        return "Elements with __artifact scribble should be converted to artifacts";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        PdfString title = ctx.node().getPdfObject().getAsString(PdfName.T);
        if (title == null) {
            return true;
        }
        if (ARTIFACT_SCRIBBLE.equals(title.toUnicodeString())) {
            issues.add(
                    new Issue(
                            IssueType.FLAGGED_ARTIFACT,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Element flagged as artifact via scribble",
                            new MistaggedArtifactFix(ctx.node())));
            return false;
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
