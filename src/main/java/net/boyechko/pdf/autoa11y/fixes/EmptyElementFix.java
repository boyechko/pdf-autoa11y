// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Removes empty structure elements from the tree, cascading upward: if removing a child makes the
 * parent empty, the parent is removed too (like {@code rm -r}).
 */
public class EmptyElementFix implements IssueFix {

    private final List<PdfStructElem> elements;
    private int removedCount;

    public EmptyElementFix(List<PdfStructElem> elements) {
        this.elements = elements;
    }

    @Override
    public void apply(DocContext ctx) {
        removedCount = 0;
        for (PdfStructElem elem : elements) {
            removedCount += StructTree.pruneEmpty(elem);
        }
    }

    @Override
    public String describe() {
        return "Removed " + removedCount + " empty structure element(s)";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), IssueLoc.none());
    }

    @Override
    public String groupLabel() {
        return "empty structure elements removed";
    }
}
