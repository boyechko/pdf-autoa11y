// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base for checks that walk the PDF structure tree. Subclasses override {@link
 * #enterElement} and/or {@link #leaveElement} to inspect each node, accumulating issues via {@link
 * #getIssues}. StructTreeCheck instances are single-run and may hold mutable traversal state.
 * Callers must provide a new instance for each document/traversal.
 *
 * <p>Implements {@link Check} so that tree-walking checks and document-level checks share a common
 * interface. The {@link #findIssues} method creates a {@link StructTreeWalker} internally, walks
 * the tree, and returns the collected issues.
 */
public abstract class StructTreeCheck implements Check {
    private static final Logger logger = LoggerFactory.getLogger(StructTreeCheck.class);

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
            logger.debug("No structure tree found, skipping {}", name());
            return new IssueList();
        }

        StructTreeWalker walker = new StructTreeWalker();
        walker.addVisitor(this);
        return walker.walk(root, ctx);
    }

    /**
     * Called before visiting {@code ctx} and returns {@code false} to skip traversing this
     * element's children.
     */
    public boolean enterElement(StructTreeContext ctx) {
        return true;
    }

    /** Called after {@code ctx} is visited (and after any children are traversed or skipped). */
    public void leaveElement(StructTreeContext ctx) {}

    /**
     * Called once before traversal begins. No current node exists yet, so only the document context
     * is supplied.
     */
    public void beforeTraversal(DocContext docCtx) {}

    /** Called once after traversal completes. */
    public void afterTraversal(DocContext docCtx) {}

    public abstract IssueList getIssues();

    @Override
    public Set<Class<? extends Check>> prerequisites() {
        return Set.of();
    }
}
