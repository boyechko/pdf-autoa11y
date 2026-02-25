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

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes empty structure elements from the tree, cascading upward: if removing a child makes the
 * parent empty, the parent is removed too (like {@code rm -r}).
 */
public class RemoveEmptyElements implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(RemoveEmptyElements.class);
    private static final int P_REMOVE_EMPTY = 25; // After structure fixes (20)

    private final List<PdfStructElem> elements;
    private int removedCount;

    public RemoveEmptyElements(List<PdfStructElem> elements) {
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
            removeIfEmpty(elem);
        }
    }

    private void removeIfEmpty(PdfStructElem elem) {
        if (!isEmpty(elem)) {
            return;
        }

        IStructureNode parent = elem.getParent();
        if (parent == null) {
            return;
        }
        // Never remove the Document element
        if (parent instanceof PdfStructTreeRoot) {
            return;
        }

        String role = elem.getRole() != null ? elem.getRole().getValue() : "unknown";
        logger.debug("Removing empty {} (obj. #{})", role, StructTree.objNum(elem));
        StructTree.removeFromParent(elem, parent);
        removedCount++;

        // Cascade: if parent is now empty, remove it too
        if (parent instanceof PdfStructElem parentElem) {
            removeIfEmpty(parentElem);
        }
    }

    private boolean isEmpty(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        return kids == null || kids.isEmpty();
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
