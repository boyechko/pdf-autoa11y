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
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Walks the PDF structure tree once, invoking multiple visitors at each node. */
public class StructureTreeWalker {
    private static final Logger logger = LoggerFactory.getLogger(StructureTreeWalker.class);

    private final TagSchema schema;
    private final List<StructureTreeVisitor> visitors = new ArrayList<>();

    private PdfStructTreeRoot root;
    private DocumentContext docCtx;
    private int globalIndex;

    public StructureTreeWalker(TagSchema schema) {
        this.schema = schema;
    }

    public StructureTreeWalker addVisitor(StructureTreeVisitor visitor) {
        visitors.add(visitor);
        return this;
    }

    public IssueList walk(PdfStructTreeRoot root, DocumentContext docCtx) {
        this.root = root;
        this.docCtx = docCtx;
        this.globalIndex = 0;

        for (StructureTreeVisitor visitor : visitors) {
            // No node context exists at the root level.
            visitor.beforeTraversal(null);
        }

        walkRoot();

        IssueList allIssues = new IssueList();
        for (StructureTreeVisitor visitor : visitors) {
            visitor.afterTraversal();
            allIssues.addAll(visitor.getIssues());
        }

        return allIssues;
    }

    private void walkRoot() {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                walkElement(elem, "/", 0);
            }
        }
    }

    private void walkElement(PdfStructElem node, String parentPath, int depth) {
        globalIndex++;

        VisitorContext ctx = buildContext(node, parentPath, depth);

        // Call enterElement on all visitors; track if any want to skip children
        boolean continueToChildren = true;
        for (StructureTreeVisitor visitor : visitors) {
            try {
                if (!visitor.enterElement(ctx)) {
                    continueToChildren = false;
                }
            } catch (Exception e) {
                logger.error(
                        "Error in visitor {} at {}: {}",
                        visitor.name(),
                        ctx.path(),
                        e.getMessage());
            }
        }

        if (continueToChildren) {
            for (PdfStructElem child : ctx.children()) {
                walkElement(child, ctx.path() + ".", depth + 1);
            }
        }

        for (StructureTreeVisitor visitor : visitors) {
            try {
                visitor.leaveElement(ctx);
            } catch (Exception e) {
                logger.error(
                        "Error in visitor {} leaving {}: {}",
                        visitor.name(),
                        ctx.path(),
                        e.getMessage());
            }
        }
    }

    private VisitorContext buildContext(PdfStructElem node, String parentPath, int depth) {
        String role = mappedRole(node);
        String path = parentPath + role + "[" + globalIndex + "]";
        TagSchema.Rule schemaRule = schema != null ? schema.roles.get(role) : null;

        PdfStructElem parent = parentOf(node);
        String parentRole = parent != null ? mappedRole(parent) : null;

        List<PdfStructElem> children = structKidsOf(node);
        List<String> childRoles = children.stream().map(this::mappedRole).toList();

        return new VisitorContext(
                node,
                path,
                role,
                schemaRule,
                parentRole,
                children,
                childRoles,
                depth,
                globalIndex,
                docCtx);
    }

    private String mappedRole(PdfStructElem n) {
        PdfDictionary roleMap = root.getRoleMap();
        PdfName role = n.getRole();

        if (roleMap != null) {
            PdfName mappedRole = roleMap.getAsName(role);
            return (mappedRole != null) ? mappedRole.getValue() : role.getValue();
        }
        return role.getValue();
    }

    private PdfStructElem parentOf(PdfStructElem n) {
        IStructureNode p = n.getParent();
        return (p instanceof PdfStructElem) ? (PdfStructElem) p : null;
    }

    private List<PdfStructElem> structKidsOf(PdfStructElem n) {
        List<IStructureNode> kids = n.getKids();
        if (kids == null) return List.of();

        List<PdfStructElem> out = new ArrayList<>();
        for (IStructureNode k : kids) {
            if (k instanceof PdfStructElem) {
                out.add((PdfStructElem) k);
            }
        }
        return out;
    }
}
