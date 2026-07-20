// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.document.DocContext;
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
    public void beforeTraversal(DocContext docCtx) {
        PdfStructTreeRoot root = docCtx.doc().getStructTreeRoot();
        if (root != null
                && StructTree.clearScribbleSegmentsInTree(
                        root, ScribbledInstructionFix.CHECK_SCRIBBLE_PREFIX)) {
            docCtx.markDirty();
        }
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        DocValue.Scribble scribble = DocValue.Scribble.of(ctx.node());
        if (scribble == null) {
            return true;
        }
        String instruction = instructionSegment(scribble);
        if (instruction != null) {
            issues.add(
                    new Issue(
                            IssueType.SCRIBBLED_INSTRUCTION,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Scribbled instruction: " + scribble.value(),
                            new ScribbledInstructionFix(ctx.node(), instruction)));
            return !ScribbledInstructionFix.isSubtreeInstruction(instruction);
        }
        return true;
    }

    /** Returns the first instruction-bearing segment, or null when the scribble has none. */
    private static String instructionSegment(DocValue.Scribble scribble) {
        for (String segment : scribble.segments()) {
            if (segment.startsWith(SCRIBBLED_INSTRUCTION_PREFIX)) {
                return segment;
            }
        }
        return null;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
