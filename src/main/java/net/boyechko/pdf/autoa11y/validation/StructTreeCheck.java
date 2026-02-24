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
// Callers must provide a new instance for each document/traversal.
///
/// Implements [Check] so that tree-walking checks and document-level checks share a common
/// interface. The [#findIssues] method creates a [StructTreeWalker] internally, walks
/// the tree, and returns the collected issues.
public abstract class StructTreeCheck implements Check {

    public abstract String name();

    public abstract String description();

    public CheckType type() {
        return CheckType.STRUCT_TREE;
    }

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

    ///  Called before visiting `ctx` and returns `false` to skip traversing this element's
    // children.
    public boolean enterElement(StructTreeContext ctx) {
        return true;
    }

    /// Called after `ctx` is visited (and after any children are traversed or skipped).
    public void leaveElement(StructTreeContext ctx) {}

    /// Called once before traversal begins; `ctx` is `null` because no current node exists at root
    // level.
    public void beforeTraversal(StructTreeContext ctx) {}

    /// Called once after traversal completes.
    public void afterTraversal() {}

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

    /// Returns checker classes that must run before this checker.
    public Set<Class<? extends StructTreeCheck>> prerequisites() {
        return Set.of();
    }
}
