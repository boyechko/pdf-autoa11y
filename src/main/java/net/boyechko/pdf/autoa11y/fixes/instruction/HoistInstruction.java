// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * {@code !HOIST}: collapses the chain of wrappers above the element, each holding nothing but the
 * one below it, so the element takes the topmost wrapper's place. The chain stops below the first
 * ancestor with other kids, which keeps reading order without any splitting, and never consumes a
 * top-level element such as Document. An element whose parent holds anything else, marked content
 * included, is refused.
 */
record HoistInstruction() implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        if (!(element.getParent() instanceof PdfStructElem top)
                || !(top.getParent() instanceof PdfStructElem)
                || StructTree.kidsOf(top).size() != 1) {
            throw new IllegalArgumentException(
                    "!HOIST refuses: parent holds other kids or is a top-level element");
        }
        int depth = 1;
        while (top.getParent() instanceof PdfStructElem up
                && up.getParent() instanceof PdfStructElem
                && StructTree.kidsOf(up).size() == 1) {
            top = up;
            depth++;
        }
        PdfStructElem container = (PdfStructElem) top.getParent();
        int index = StructTree.findKidIndex(container, top);

        StructTree.materializeInheritedPage(element);
        ((PdfStructElem) element.getParent()).removeKid(element);
        container.addKid(index, element);
        container.removeKid(top);
        return receipt("HOIST past " + depth);
    }
}
