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
    private static final int P_REMOVE_EMPTY = 25; // After structure fixes (20)

    private final List<PdfStructElem> elements;
    private int removedCount;

    public EmptyElementFix(List<PdfStructElem> elements) {
        this.elements = elements;
    }

    @Override
    public int priority() {
        return P_REMOVE_EMPTY;
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
