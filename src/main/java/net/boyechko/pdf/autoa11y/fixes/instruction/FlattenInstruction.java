// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;

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
        for (IStructureNode kid : StructTree.kidsOf(element)) {
            if (kid instanceof PdfStructElem se) {
                refuseMixedContent(se);
            }
        }

        int hoisted = 0;
        int insertAt = 0;
        for (IStructureNode kid : new ArrayList<>(StructTree.kidsOf(element))) {
            if (!(kid instanceof PdfStructElem intermediate) || isLeaf(intermediate)) {
                insertAt++;
                continue;
            }
            // Insert ahead of the intermediate, so hoistees land where their subtree was, then
            // drop the emptied subtree. Its leaves' ancestry stays intact until each is detached,
            // which is what lets the inherited page be written down.
            for (PdfStructElem leaf : leavesOf(intermediate)) {
                StructTree.materializeInheritedPage(leaf);
                ((PdfStructElem) leaf.getParent()).removeKid(leaf);
                element.addKid(insertAt++, leaf);
                hoisted++;
            }
            element.removeKid(intermediate);
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
        for (IStructureNode kid : StructTree.kidsOf(elem)) {
            if (kid instanceof PdfStructElem se) {
                refuseMixedContent(se);
            } else {
                throw new IllegalArgumentException(
                        "!FLATTEN refuses: "
                                + StructTree.mappedRole(elem)
                                + " holds marked content alongside nested elements");
            }
        }
    }

    /** Returns true when the element has no structure-element kids of its own. */
    private static boolean isLeaf(PdfStructElem elem) {
        return StructTree.childrenOf(elem, PdfStructElem.class).isEmpty();
    }

    /** Collects the element's leaf descendants in document order. */
    private static List<PdfStructElem> leavesOf(PdfStructElem elem) {
        List<PdfStructElem> leaves = new ArrayList<>();
        for (PdfStructElem child : StructTree.childrenOf(elem, PdfStructElem.class)) {
            if (isLeaf(child)) {
                leaves.add(child);
            } else {
                leaves.addAll(leavesOf(child));
            }
        }
        return leaves;
    }
}
