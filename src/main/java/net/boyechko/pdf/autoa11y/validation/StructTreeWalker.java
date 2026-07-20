// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks the PDF structure tree once, invoking one or more visitors at each node. Subtrees the user
 * has marked verified (see {@link StructTree#isVerified}) are skipped entirely, so no check sees or
 * touches them.
 */
public class StructTreeWalker {
    private static final Logger logger = LoggerFactory.getLogger(StructTreeWalker.class);

    private final TagSchema schema;
    private final List<StructTreeCheck> visitors = new ArrayList<>();

    private PdfStructTreeRoot root;
    private DocContext docCtx;
    private int globalIndex;

    public StructTreeWalker(TagSchema schema) {
        this.schema = schema;
    }

    public StructTreeWalker addVisitor(StructTreeCheck visitor) {
        visitors.add(visitor);
        return this;
    }

    public IssueList walk(PdfStructTreeRoot root, DocContext docCtx) {
        this.root = root;
        this.docCtx = docCtx;
        this.globalIndex = 0;

        for (StructTreeCheck visitor : visitors) {
            visitor.beforeTraversal(docCtx);
        }

        walkRoot();

        IssueList allIssues = new IssueList();
        for (StructTreeCheck visitor : visitors) {
            visitor.afterTraversal(docCtx);
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
        if (StructTree.isVerified(node)) {
            logger.debug(
                    "Skipping user-verified subtree at {}{} #{}",
                    parentPath,
                    StructTree.mappedRole(node),
                    StructTree.objNum(node));
            return;
        }

        globalIndex++;

        StructTreeContext ctx =
                StructTreeContext.fromNode(node, parentPath, depth, globalIndex, schema, docCtx);

        // Call enterElement on all visitors; track if any want to skip children
        boolean continueToChildren = true;
        for (StructTreeCheck visitor : visitors) {
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

        for (StructTreeCheck visitor : visitors) {
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
}
