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

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a group of consecutive raw kids (MCRs and struct elements) inside a parent element into an
 * L > LI > LBody > P structure. The L element is placed as a sibling of the parent, reusing an
 * existing adjacent L if one exists. The bullet y-position determines insertion order within the L.
 */
public final class WrapBulletAlignedKidsInLBody implements IssueFix {

    private static final Logger logger =
            LoggerFactory.getLogger(WrapBulletAlignedKidsInLBody.class);

    private final PdfStructElem parent;
    private final List<Integer> kidIndices;
    private final float bulletY;

    public WrapBulletAlignedKidsInLBody(
            PdfStructElem parent, List<Integer> kidIndices, float bulletY) {
        this.parent = parent;
        this.kidIndices = kidIndices;
        this.bulletY = bulletY;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        /*
         * For moving MCRs between parents, raw K-array manipulation is
         * unavoidable (iText has no MCR-move API). But for struct element
         * insertion, always use addKid() to ensure proper indirect reference
         * handling. The hybrid approach works: remove items from the K array
         * manually, but insert new struct elements via addKid.
         */
        PdfArray parentK = StructTree.normalizeKArray(parent);
        if (parentK == null) {
            return;
        }

        // 1. Collect objects to move from parent's K array (forward order)
        List<PdfObject> collectedObjects = new ArrayList<>();
        for (int idx : kidIndices) {
            if (idx < parentK.size()) {
                collectedObjects.add(parentK.get(idx, false));
            }
        }
        if (collectedObjects.isEmpty()) {
            return;
        }

        // 2. Remove from parent K array in reverse order to preserve indices
        for (int i = kidIndices.size() - 1; i >= 0; i--) {
            int idx = kidIndices.get(i);
            if (idx < parentK.size()) {
                parentK.remove(idx);
            }
        }

        // 3. Build L > LI > LBody > P structure as a sibling of the parent
        PdfStructElem listElem = findOrCreateListElement(ctx);
        PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
        int insertPos = findInsertPosition(listElem, ctx);
        listElem.addKid(insertPos, li);

        PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        li.addKid(lBody);

        PdfStructElem newP = new PdfStructElem(ctx.doc(), PdfName.P);
        if (parent.getPdfObject().containsKey(PdfName.Pg)) {
            newP.getPdfObject().put(PdfName.Pg, parent.getPdfObject().get(PdfName.Pg));
        }
        lBody.addKid(newP);

        // 4. Build newP's K array with the collected objects
        PdfArray newPK = new PdfArray();
        newP.getPdfObject().put(PdfName.K, newPK);

        for (PdfObject obj : collectedObjects) {
            newPK.add(obj);
            // Update /P reference on struct elem kids (stored as indirect refs)
            PdfObject resolved =
                    obj.isIndirectReference()
                            ? ctx.doc()
                                    .getPdfObject(
                                            ((com.itextpdf.kernel.pdf.PdfIndirectReference) obj)
                                                    .getObjNumber())
                            : obj;
            if (resolved instanceof PdfDictionary dict && dict.containsKey(PdfName.S)) {
                dict.put(PdfName.P, newP.getPdfObject());
            }
        }

        logger.debug(
                "Wrapped {} raw kids into L>LI>LBody>P near obj. #{} (bulletY={})",
                kidIndices.size(),
                StructTree.objNum(parent),
                String.format("%.1f", bulletY));
    }

    /** Finds an existing L element adjacent to the parent, or creates one. */
    private PdfStructElem findOrCreateListElement(DocContext ctx) {
        IStructureNode container = parent.getParent();
        if (!(container instanceof PdfStructElem containerElem)) {
            // Fallback: create L as child of parent (will be caught by schema check)
            PdfStructElem l = new PdfStructElem(ctx.doc(), PdfName.L);
            parent.addKid(l);
            return l;
        }

        int parentIndex = StructTree.findKidIndex(containerElem, parent);
        if (parentIndex < 0) {
            PdfStructElem l = new PdfStructElem(ctx.doc(), PdfName.L);
            parent.addKid(l);
            return l;
        }

        // Look for an existing L element immediately after the parent
        List<IStructureNode> containerKids = containerElem.getKids();
        int listIndex = parentIndex + 1;
        if (listIndex < containerKids.size()) {
            IStructureNode nextSibling = containerKids.get(listIndex);
            if (nextSibling instanceof PdfStructElem nextElem
                    && "L".equals(StructTree.mappedRole(nextElem))) {
                return nextElem;
            }
        }

        // No adjacent L — create one after the parent
        PdfStructElem l = new PdfStructElem(ctx.doc(), PdfName.L);
        StructTree.addKidToParent(containerElem, listIndex, l);
        return l;
    }

    /** Determines where to insert the new LI based on bullet y-position. */
    private int findInsertPosition(PdfStructElem listElem, DocContext ctx) {
        int pageNum = StructTree.determinePageNumber(ctx, parent);
        List<PdfStructElem> existingLIs = StructTree.childrenOf(listElem, PdfStructElem.class);

        for (int i = 0; i < existingLIs.size(); i++) {
            Rectangle liBounds = Content.getBoundsForElement(existingLIs.get(i), ctx, pageNum);
            if (liBounds != null) {
                float liCenterY = liBounds.getBottom() + liBounds.getHeight() / 2;
                if (bulletY > liCenterY) {
                    return i;
                }
            }
        }

        return existingLIs.size();
    }

    @Override
    public String describe() {
        return "Wrapped " + kidIndices.size() + " bullet-aligned kids in LBody";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), IssueLoc.atElem(ctx, parent));
    }
}
