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

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;

/**
 * Abstract base for checks that walk the PDF structure tree. Subclasses override {@link
 * #enterElement} and/or {@link #leaveElement} to inspect each node, accumulating issues via {@link
 * #getIssues}.
 *
 * <p>Implements {@link Check} so that tree-walking checks and document-level checks share a common
 * interface. The {@link #findIssues} method creates a {@link StructTreeWalker} internally, walks
 * the tree, and returns the collected issues.
 */
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

    public boolean enterElement(StructTreeContext ctx) {
        return true;
    }

    public void leaveElement(StructTreeContext ctx) {}

    public void beforeTraversal(StructTreeContext ctx) {}

    public void afterTraversal() {}

    public abstract IssueList getIssues();

    /**
     * Returns checker classes that must run before this checker. The pipeline validates at
     * construction time that all prerequisites appear earlier in the checker list.
     */
    public Set<Class<? extends StructTreeCheck>> prerequisites() {
        return Set.of();
    }
}
