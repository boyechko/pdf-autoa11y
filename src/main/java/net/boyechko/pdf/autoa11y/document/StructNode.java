// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * Anything a structure element may hold (PDF 1.7, 14.7.4.1): another structure element, or a
 * reference to a content item, which is either a marked-content sequence or a whole PDF object.
 * Unlike iText's {@link IStructureNode} it is closed over exactly those three, leaves out the
 * structure tree root, and keeps an object reference apart from marked content.
 */
public sealed interface StructNode permits Tag, MarkedContent, ObjectRef {

    /** The node standing for an iText kid; the structure tree root is no one's kid. */
    static StructNode of(IStructureNode kid) {
        return switch (kid) {
            case PdfStructElem elem -> Tag.of(elem);
            // Before PdfMcr, which PdfObjRef extends.
            case PdfObjRef objRef -> ObjectRef.of(objRef);
            case PdfMcr mcr -> MarkedContent.of(mcr);
            default -> throw new IllegalArgumentException("Not a kid: " + kid.getClass());
        };
    }

    /** The page the node sits or renders on, or zero when it cannot be resolved. */
    int page();

    /** The structure element holding this node, or null directly under the tree root. */
    Tag parent();

    /**
     * Moves the node out of its parent to become the last kid of another, keeping it on the page it
     * renders on. Returns the node as it stands afterwards, which is the one to keep using: a
     * content reference may be rewritten to carry its own page.
     */
    StructNode moveTo(Tag newParent);

    /**
     * Moves the node out of its parent to sit right before the sibling, on the page it renders on.
     */
    void insertBefore(Tag sibling);

    /** Takes the node out of its parent, along with anything it holds. */
    void remove();
}
