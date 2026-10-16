// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.StructTree.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code !ADD_PARENT <chain>}: wraps the element in a chain of new parents. The tag expression must
 * be a linear chain (each node has at most one child and the innermost is a leaf); for example,
 * {@code Reference[Link[P[]]]} applied to a Span produces {@code Reference[Link[P[Span]]]}.
 */
record AddParentInstruction(String tagExpr) implements ScribbledInstruction {
    private static final Logger logger = LoggerFactory.getLogger(AddParentInstruction.class);

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot add parent: element has no parent");
            return null;
        }

        List<Node<String>> nodes = Node.fromString(tagExpr);
        if (nodes.size() != 1) {
            throw new IllegalArgumentException(
                    "!ADD_PARENT requires exactly one wrapper chain root, got: " + tagExpr);
        }

        List<String> chain = linearChain(nodes.get(0));

        int index = StructTree.findKidIndex(parent, element);

        // Build the chain top-down: each wrapper must have /P set (via addKid on its own
        // parent) before we can addKid into it.
        PdfStructElem innermost = parent;
        PdfStructElem outermost = null;
        for (String wrapperName : chain) {
            PdfStructElem wrapper = new PdfStructElem(ctx.doc(), RoleMap.toPdfName(wrapperName));
            if (outermost == null) {
                parent.addKid(index, wrapper);
                outermost = wrapper;
            } else {
                innermost.addKid(wrapper);
            }
            innermost = wrapper;
        }

        parent.removeKid(element);
        innermost.addKid(element);
        return receipt("ADD_PARENT " + tagExpr);
    }

    /**
     * Flattens a linear chain of single-child nodes into a list of role names, outermost first.
     * Rejects branching (more than one child at any level).
     */
    private List<String> linearChain(Node<String> root) {
        List<String> names = new ArrayList<>();
        Node<String> current = root;
        while (true) {
            names.add(current.value());
            List<Node<String>> kids = current.children();
            if (kids.isEmpty()) {
                return names;
            }
            if (kids.size() > 1) {
                throw new IllegalArgumentException(
                        "!ADD_PARENT requires a linear chain (one child per level), got: "
                                + tagExpr);
            }
            current = kids.get(0);
        }
    }
}
