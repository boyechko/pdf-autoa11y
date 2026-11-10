// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.Tag;

/**
 * Immutable context passed to visitors during structure tree traversal. Contains pre-computed
 * information about the current node, its position in the tree, and relationships to parent and
 * children.
 */
public record Visit(
        Tag tag,
        String path,
        String role,
        String mappedRole,
        String parentRole,
        List<PdfStructElem> children,
        List<String> childRoles,
        /** Depth in the tree (0 = direct child of root). */
        int depth,
        /** Index in traversal order (1-based). */
        int globalIndex,
        DocContext docCtx) {

    /**
     * Creates a Visit from a PdfStructElem and internal state.
     *
     * @param node The current node being visited.
     * @param parentPath The path to the parent of the current node.
     * @param depth The depth of the current node in the tree.
     * @param globalIndex The index of the current node in the traversal order.
     * @param docCtx The document context.
     * @return A new Visit.
     */
    public static Visit fromNode(
            PdfStructElem node, String parentPath, int depth, int globalIndex, DocContext docCtx) {
        Tag tag = Tag.of(node);
        // TODO: Update callers to use mappedRole, then change to PdfName.
        String role = tag.role();
        String mappedRole = tag.role();
        String path = parentPath + role + "[" + globalIndex + "]";

        Tag parent = tag.parent();
        String parentRole = parent != null ? parent.role() : null;

        List<Tag> childTags = tag.children();
        List<PdfStructElem> children = childTags.stream().map(Tag::elem).toList();
        List<String> childRoles = childTags.stream().map(Tag::role).toList();

        return new Visit(
                tag,
                path,
                role,
                mappedRole,
                parentRole,
                children,
                childRoles,
                depth,
                globalIndex,
                docCtx);
    }

    /** The visited element as iText's own type. */
    public PdfStructElem node() {
        return tag.elem();
    }

    public PdfDocument doc() {
        return docCtx.doc();
    }

    public int page() {
        return tag.page();
    }

    public int objNum() {
        return tag.objNum();
    }

    public Locus locus() {
        return Locus.atElem(docCtx, node());
    }

    public boolean hasRole(String roleName) {
        return roleName.equals(role);
    }

    public boolean hasRole(PdfName roleName) {
        return roleName.equals(node().getRole());
    }

    public boolean hasAnyRole(String... roleNames) {
        for (String r : roleNames) {
            if (r.equals(role)) return true;
        }
        return false;
    }
}
