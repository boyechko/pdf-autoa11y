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

import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.ScribbledInstructionFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/** Detects elements whose /T scribble encodes a structural instruction. */
public class ScribbledInstructionCheck extends StructTreeCheck {
    static final String SCRIBBLED_INSTRUCTION_PREFIX = "!";
    static final String ARTIFACT_INSTRUCTION = "ARTIFACT";

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Scribbled Instruction Check";
    }

    @Override
    public String description() {
        return "Elements with structural-instruction scribbles should be processed";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        StructTree.clearScribbleSegments(ctx.node(), ScribbledInstructionFix.CHECK_SCRIBBLE_PREFIX);
        DocValue.Scribble scribble = DocValue.Scribble.of(ctx.node());
        if (scribble == null) {
            return true;
        }
        if (scribble.value().startsWith(SCRIBBLED_INSTRUCTION_PREFIX)) {
            issues.add(
                    new Issue(
                            IssueType.SCRIBBLED_INSTRUCTION,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Scribbled instruction: " + scribble.value(),
                            new ScribbledInstructionFix(ctx.node(), scribble.value())));
            // Skip children for ARTIFACT — the entire subtree will be converted
            return !scribble.value()
                    .startsWith(SCRIBBLED_INSTRUCTION_PREFIX + ARTIFACT_INSTRUCTION);
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
