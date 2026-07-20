// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/// Abstract base for checks that walk the PDF structure tree. Subclasses override
/// [#enterElement] and/or [#leaveElement] to inspect each node, accumulating issues via
/// [#getIssues]. StructTreeCheck instances are single-run and may hold mutable traversal state.
/// Callers must provide a new instance for each document/traversal.
///
/// Implements [Check] so that tree-walking checks and document-level checks share a common
/// interface. The [#findIssues] method creates a [StructTreeWalker] internally, walks
/// the tree, and returns the collected issues.
public abstract class StructTreeCheck implements Check {

    public abstract String name();

    public abstract String description();

    @Override
    public String passedMessage() {
        return name() + ": no issues";
    }

    @Override
    public String failedMessage() {
        return name() + ": issues found";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            return new IssueList();
        }

        StructTreeWalker walker = new StructTreeWalker(TagSchema.loadDefault());
        walker.addVisitor(this);
        return walker.walk(root, ctx);
    }

    /// Called before visiting `ctx` and returns `false` to skip traversing this element's
    /// children.
    public boolean enterElement(StructTreeContext ctx) {
        return true;
    }

    /// Called after `ctx` is visited (and after any children are traversed or skipped).
    public void leaveElement(StructTreeContext ctx) {}

    /// Called once before traversal begins. No current node exists yet, so only the document
    /// context is supplied.
    public void beforeTraversal(DocContext docCtx) {}

    /// Called once after traversal completes.
    public void afterTraversal(DocContext docCtx) {}

    public abstract IssueList getIssues();

    /// Returns an [IssueLoc] for [StructTreeContext#node()] in `ctx`.
    protected static IssueLoc locAtElem(StructTreeContext ctx) {
        return IssueLoc.atElem(ctx.node(), ctx.getPageNumber(), ctx.role(), ctx.path());
    }

    /// Returns an [IssueLoc] for `element` using page/role/path from `ctx`.
    protected static IssueLoc locAtElem(StructTreeContext ctx, PdfStructElem element) {
        return IssueLoc.atElem(
                element, ctx.getPageNumber(), StructTree.mappedRole(element), ctx.path());
    }

    @Override
    public Set<Class<? extends Check>> prerequisites() {
        return Set.of();
    }
}
