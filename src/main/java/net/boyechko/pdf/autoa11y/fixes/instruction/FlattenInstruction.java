// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructNode;
import net.boyechko.pdf.autoa11y.document.Tag;

/**
 * {@code !FLATTEN}: hoists every leaf element in the element's subtree to become an immediate kid
 * of it, in document order, and removes the intermediate elements the hoisting empties. A leaf is a
 * structure element with no structure-element kids of its own, so its own marked content rides
 * along; kids that are already leaves, and the element's own marked content, stay where they are.
 * An intermediate holding marked content directly alongside nested elements is refused before any
 * mutation, since hoisting its leaves would orphan that content.
 */
record FlattenInstruction() implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        // Validate the whole subtree first, so a refusal is side-effect free.
        for (Tag child : Tag.of(element).children()) {
            refuseMixedContent(child.elem());
        }

        int hoisted = 0;
        int insertAt = 0;
        for (StructNode kid : Tag.of(element).kids()) {
            if (!(kid instanceof Tag tag) || isLeaf(tag.elem())) {
                insertAt++;
                continue;
            }
            PdfStructElem intermediate = tag.elem();
            // Insert ahead of the intermediate, so hoistees land where their subtree was, then
            // drop the emptied subtree. Its leaves' ancestry stays intact until each is detached,
            // which is what lets the inherited page be written down.
            for (PdfStructElem leaf : leavesOf(intermediate)) {
                Tag.of(leaf).materializeInheritedPage();
                Tag.of(leaf).remove();
                element.addKid(insertAt++, leaf);
                hoisted++;
            }
            tag.remove();
        }
        return receipt("FLATTEN hoisted " + hoisted);
    }

    @Override
    public boolean coversSubtree() {
        return true;
    }

    /**
     * Refuses the flatten if any intermediate element mixes marked content with nested elements.
     */
    private static void refuseMixedContent(PdfStructElem elem) {
        if (isLeaf(elem)) {
            return;
        }
        for (StructNode kid : Tag.of(elem).kids()) {
            if (kid instanceof Tag child) {
                refuseMixedContent(child.elem());
            } else {
                throw new IllegalArgumentException(
                        "!FLATTEN refuses: "
                                + Tag.of(elem).role()
                                + " holds marked content alongside nested elements");
            }
        }
    }

    /** Returns true when the element has no structure-element kids of its own. */
    private static boolean isLeaf(PdfStructElem elem) {
        return Tag.of(elem).children().isEmpty();
    }

    /** Collects the element's leaf descendants in document order. */
    private static List<PdfStructElem> leavesOf(PdfStructElem elem) {
        List<PdfStructElem> leaves = new ArrayList<>();
        for (Tag child : Tag.of(elem).children()) {
            if (isLeaf(child.elem())) {
                leaves.add(child.elem());
            } else {
                leaves.addAll(leavesOf(child.elem()));
            }
        }
        return leaves;
    }
}
