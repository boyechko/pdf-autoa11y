// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.ListItemScribble;

/**
 * {@code !BREAK_OUT}: takes a stray item back out of its list. The content of its LBody moves up
 * beside the list, which is split around it so reading order holds. Items before it stay in the
 * original list, items after it move to a new list placed after the content, and a list left empty
 * is removed. Marked as {@code !BREAK_OUT} on each stray item, a run comes out in one pass: every
 * fix splits whichever list holds its item by then. The item is destroyed, so the receipt is the
 * event stamped on the surviving lists instead.
 */
record BreakOutInstruction() implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        if (!"LI".equals(StructTree.mappedRole(element))) {
            throw new IllegalArgumentException("!BREAK_OUT requires an LI element");
        }
        if (!(element.getParent() instanceof PdfStructElem list)
                || !(list.getParent() instanceof PdfStructElem container)) {
            throw new IllegalArgumentException("!BREAK_OUT requires an LI inside a list element");
        }
        int itemIndex = StructTree.findKidIndex(list, element);
        int listIndex = StructTree.findKidIndex(container, list);
        if (itemIndex < 0 || listIndex < 0) {
            throw new IllegalArgumentException(
                    "!BREAK_OUT refuses: item or list is not among its parent's kids (dangling /P)");
        }
        List<PdfStructElem> content = itemContentOf(element);

        List<IStructureNode> listKids = new ArrayList<>(StructTree.kidsOf(list));
        List<IStructureNode> trailing = listKids.subList(itemIndex + 1, listKids.size());
        boolean leadingItems = itemIndex > 0;
        PdfStructElem tail = null;
        if (leadingItems && !trailing.isEmpty()) {
            tail = new PdfStructElem(ctx.doc(), list.getRole());
            PdfObject attributes = list.getPdfObject().get(PdfName.A);
            if (attributes != null) {
                tail.put(PdfName.A, attributes.clone());
            }
            container.addKid(listIndex + 1, tail);
            for (IStructureNode kid : trailing) {
                StructTree.moveKid(kid, list, tail);
            }
        }

        // With no items ahead of it, the content goes before the list, which keeps the rest.
        int insertAt = leadingItems ? listIndex + 1 : listIndex;
        for (PdfStructElem hoistee : content) {
            StructTree.materializeInheritedPage(hoistee);
            ((PdfStructElem) hoistee.getParent()).removeKid(hoistee);
            container.addKid(insertAt++, hoistee);
        }
        list.removeKid(element);

        if (StructTree.kidsOf(list).isEmpty()) {
            container.removeKid(list);
        } else {
            ListItemScribble.update(list, receipt("BREAK_OUT"));
        }
        if (tail != null) {
            ListItemScribble.update(tail, receipt("BREAK_OUT"));
        }
        return null;
    }

    /**
     * Returns the elements inside the item's LBody kids, refusing an item that holds anything else
     * (a Lbl or loose marked content) or a body holding marked content directly, since those would
     * be orphaned or dropped by the hoist.
     */
    private static List<PdfStructElem> itemContentOf(PdfStructElem item) {
        List<PdfStructElem> content = new ArrayList<>();
        for (IStructureNode kid : StructTree.kidsOf(item)) {
            if (!(kid instanceof PdfStructElem body)
                    || !"LBody".equals(StructTree.mappedRole(body))) {
                throw new IllegalArgumentException(
                        "!BREAK_OUT refuses: item holds something other than LBody");
            }
            for (IStructureNode bodyKid : StructTree.kidsOf(body)) {
                if (!(bodyKid instanceof PdfStructElem se)) {
                    throw new IllegalArgumentException(
                            "!BREAK_OUT refuses: LBody holds marked content directly");
                }
                content.add(se);
            }
        }
        return content;
    }
}
