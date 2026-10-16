// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * {@code !MERGE}: folds the element's kids into its preceding sibling and removes the emptied
 * element, so a run of siblings a tagger split apart reads as one. Marked as {@code !MERGE} on each
 * element to be absorbed, a whole run collapses leftward: every fix resolves its preceding sibling
 * against the current tree, so by the time the third element merges, the second is already gone.
 * The element is destroyed, so the receipt goes on the survivor instead.
 */
record MergeInstruction() implements ScribbledInstruction {

    /** Pattern for reading back the running count from an earlier merge's receipt. */
    private static final Pattern MERGED_COUNT_PATTERN =
            Pattern.compile(Pattern.quote(receipt("MERGE absorbed ")) + "(\\d+)");

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        if (!(element.getParent() instanceof PdfStructElem parent)) {
            throw new IllegalArgumentException("!MERGE requires an element with a parent element");
        }
        int index = StructTree.findKidIndex(parent, element);
        if (index < 0) {
            // /P points at the parent but the parent's /K does not list the element back:
            // a malformed tree, or a wrapper left stale by an earlier fix that detached it.
            throw new IllegalArgumentException(
                    "!MERGE refuses: element is not among its parent's kids (dangling /P)");
        }
        if (index == 0) {
            throw new IllegalArgumentException(
                    "!MERGE refuses: there is no preceding sibling element to merge into");
        }
        if (!(parent.getKids().get(index - 1) instanceof PdfStructElem target)) {
            throw new IllegalArgumentException(
                    "!MERGE refuses: preceding sibling is marked content, not an element");
        }
        if (!StructTree.mappedRole(element).equals(StructTree.mappedRole(target))) {
            throw new IllegalArgumentException(
                    "!MERGE refuses: preceding sibling has a different mapped role");
        }

        // Pin the page the absorbed element resolved through, so bare-int MCRs moving into a
        // page-less survivor still find one. Cross-page kids are handled by moveKid.
        PdfObject effectivePg = StructTree.effectivePageDict(element);
        if (effectivePg != null && StructTree.effectivePageDict(target) == null) {
            target.getPdfObject().put(PdfName.Pg, effectivePg);
            target.setModified();
        }

        for (IStructureNode kid : new ArrayList<>(StructTree.kidsOf(element))) {
            StructTree.moveKid(kid, element, target);
        }
        parent.removeKid(element);
        stampMergeReceipt(target);
        return null;
    }

    /** Stamps "INST MERGE absorbed N" on the survivor, folding in an earlier merge's count. */
    private static void stampMergeReceipt(PdfStructElem target) {
        DocValue.Scribble existing = StructTree.getScribble(target);
        int absorbed = 1;
        if (existing != null) {
            Matcher m = MERGED_COUNT_PATTERN.matcher(existing.value());
            if (m.find()) {
                absorbed += Integer.parseInt(m.group(1));
            }
        }
        StructTree.clearScribbleSegments(target, ScribbledInstructionFix.INSTRUCTION_TAG);
        StructTree.addToolScribble(target, receipt("MERGE absorbed " + absorbed));
    }
}
