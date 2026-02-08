/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;

/**
 * Immutable context passed to visitors during structure tree traversal. Contains pre-computed
 * information about the current node, its position in the tree, and relationships to parent and
 * children.
 */
public record VisitorContext(
        PdfStructElem node,
        String path,
        String role,
        TagSchema.Rule schemaRule,
        String parentRole,
        List<PdfStructElem> children,
        List<String> childRoles,
        /** Depth in the tree (0 = direct child of root). */
        int depth,
        /** Index in traversal order (1-based). */
        int globalIndex,
        DocumentContext docCtx) {

    public PdfDocument doc() {
        return docCtx.doc();
    }

    public int getPageNumber() {
        PdfDictionary dict = node.getPdfObject();
        PdfDictionary pg = dict.getAsDictionary(PdfName.Pg);

        if (pg != null) {
            return doc().getPageNumber(pg);
        }

        int objNum = StructureTree.objNumber(node);
        if (objNum >= 0) {
            return docCtx.getPageNumber(objNum);
        }

        return 0;
    }

    public int getObjNum() {
        return StructureTree.objNumber(node);
    }

    public boolean hasRole(String roleName) {
        return roleName.equals(role);
    }

    public boolean hasAnyRole(String... roleNames) {
        for (String r : roleNames) {
            if (r.equals(role)) return true;
        }
        return false;
    }
}
