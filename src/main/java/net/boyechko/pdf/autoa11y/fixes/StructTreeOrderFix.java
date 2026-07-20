// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Comparator;
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
public class StructTreeOrderFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(StructTreeOrderFix.class);

    private final PdfStructElem element;
    private final Map<Integer, ReadingPosition> cache;
    private int reorderedCount;
    private int totalChildren;

    public StructTreeOrderFix(PdfStructElem element, Map<Integer, ReadingPosition> cache) {
        this.element = element;
        this.cache = cache;
        this.reorderedCount = 0;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        List<PdfStructElem> children = StructTree.childrenOf(element, PdfStructElem.class);
        reorderChildren(element, children);
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
        int movedCount = 0;
        for (int i = 0; i < structIndices.size() && i < sorted.size(); i++) {
            PdfStructElem child = sorted.get(i);
            int pos = structIndices.get(i);
            PdfObject ref = child.getPdfObject().getIndirectReference();
            if (ref == null) {
                ref = child.getPdfObject();
            }
            if (!StructTree.isSame(kArray.get(pos), ref)) {
                movedCount++;
            }
            kArray.set(pos, ref);
        }
        this.reorderedCount = movedCount;
        this.totalChildren = children.size();
    }

    /** Checks whether a PdfObject in the K array refers to one of the children. */
    private boolean isStructElemRef(PdfObject obj, List<PdfStructElem> children) {
        for (PdfStructElem child : children) {
            if (StructTree.isSame(obj, child.getPdfObject())) return true;
        }
        return false;
    }

    @Override
    public String describe() {
        return "Reordered " + reorderedCount + " of " + totalChildren + " children";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, element));
    }

    @Override
    public int resolvedItemCount() {
        return reorderedCount;
    }
}
