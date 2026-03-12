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

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.checks.StructTreeOrderCheck;
import net.boyechko.pdf.autoa11y.checks.StructTreeOrderCheck.ReadingPosition;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reorders structure tree siblings to match reading order (page, then MCID within page). */
public class ReorderStructTree implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ReorderStructTree.class);
    private static final int P_REORDER = 10; // Before artifact removal (12) and flatten (15)

    private final Map<Integer, ReadingPosition> cache;
    private int reorderedCount;

    public ReorderStructTree(Map<Integer, ReadingPosition> cache) {
        this.cache = cache;
    }

    public ReorderStructTree() {
        this(new HashMap<>());
    }

    @Override
    public int priority() {
        return P_REORDER;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) return;

        for (IStructureNode kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                reorderRecursive(elem);
            }
        }
    }

    private void reorderRecursive(PdfStructElem elem) {
        List<PdfStructElem> children = StructTree.structKidsOf(elem);

        // Recurse first (bottom-up) so cache is populated
        for (PdfStructElem child : children) {
            reorderRecursive(child);
        }

        if (children.size() >= 2 && !StructTreeOrderCheck.isInOrder(children, cache)) {
            reorderChildren(elem, children);
            reorderedCount++;
        }
    }

    /**
     * Reorders struct element children within a parent's /K array by reading position. Non-struct
     * entries (MCRs, OBJRs) are left in place; only struct element references are repositioned.
     */
    private void reorderChildren(PdfStructElem parent, List<PdfStructElem> children) {
        PdfArray kArray = StructTree.normalizeKArray(parent);
        if (kArray == null) return;

        // Sort children by reading position
        List<PdfStructElem> sorted = new ArrayList<>(children);
        sorted.sort(
                Comparator.comparing(elem -> StructTreeOrderCheck.readingPositionOf(elem, cache)));

        // Find positions of struct elem refs in the K array
        List<Integer> structIndices = new ArrayList<>();
        for (int i = 0; i < kArray.size(); i++) {
            PdfObject obj = kArray.get(i);
            if (isStructElemRef(obj, children)) {
                structIndices.add(i);
            }
        }

        // Replace struct elem refs at their original positions with the sorted order
        for (int i = 0; i < structIndices.size() && i < sorted.size(); i++) {
            PdfStructElem child = sorted.get(i);
            int pos = structIndices.get(i);
            PdfObject ref = child.getPdfObject().getIndirectReference();
            if (ref == null) {
                ref = child.getPdfObject();
            }
            kArray.set(pos, ref);
        }

        logger.debug("Reordered {} struct children in {}", sorted.size(), parent.getRole());
    }

    /**
     * Checks whether a PdfObject in the K array is an indirect reference to one of the children.
     */
    private boolean isStructElemRef(PdfObject obj, List<PdfStructElem> children) {
        for (PdfStructElem child : children) {
            PdfObject childObj = child.getPdfObject();
            if (obj == childObj) return true;
            if (childObj.getIndirectReference() != null
                    && childObj.getIndirectReference().equals(obj)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return "Reordered children in " + reorderedCount + " element(s) to match reading order";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.none());
    }

    @Override
    public int resolvedItemCount() {
        return reorderedCount;
    }
}
