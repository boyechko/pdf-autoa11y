// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;

/**
 * One structural instruction parsed from a scribble, ready to be carried out on the scribbled
 * element by {@link ScribbledInstructionFix}.
 */
sealed interface ScribbledInstruction
        permits AddChildInstruction,
                AddParentInstruction,
                ArtifactInstruction,
                BreakOutInstruction,
                FlattenInstruction,
                HoistInstruction,
                MergeInstruction,
                ReorderKidsInstruction,
                SetRoleInstruction,
                SplitLinesInstruction,
                UnlinkInstruction {

    /**
     * Carries out the instruction on the element. Returns the receipt to stamp onto /T (e.g. "INST
     * SET_ROLE P -> H2"), or null to leave /T untouched because the instruction manages its own
     * scribbles or destroys the element.
     */
    String apply(DocContext ctx, PdfStructElem element) throws Exception;

    /** Returns true if the instruction rewrites the whole subtree, not just the element. */
    default boolean coversSubtree() {
        return false;
    }

    /**
     * Builds a receipt segment naming what was carried out, e.g. {@code "INST SET_ROLE P -> H2"}.
     */
    static String receipt(String done) {
        return ScribbledInstructionFix.INSTRUCTION_TAG + " " + done;
    }
}
