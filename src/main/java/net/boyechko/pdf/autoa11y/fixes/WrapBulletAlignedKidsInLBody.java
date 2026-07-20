// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
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
 *
 * <p>Kids are captured as PdfObjects at detection time and re-located by identity at apply time, so
 * earlier fixes that reshape the parent's K array cannot redirect this fix to the wrong kids.
 */
public final class WrapBulletAlignedKidsInLBody implements IssueFix {

    private static final Logger logger =
            LoggerFactory.getLogger(WrapBulletAlignedKidsInLBody.class);

    private final PdfStructElem parent;
    private final List<PdfObject> kidObjects;
    private final float bulletY;
    private final int pageNum;

    public WrapBulletAlignedKidsInLBody(
            PdfStructElem parent, List<PdfObject> kidObjects, float bulletY, int pageNum) {
        this.parent = parent;
        this.kidObjects = kidObjects != null ? List.copyOf(kidObjects) : List.of();
        this.bulletY = bulletY;
        this.pageNum = pageNum;
    }

    @Override
    public int priority() {
        return 20; // after list creation (10), before merges (30)
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        /*
         * We remove the captured kids from the parent's K array by hand (there is no bulk
         * MCR-move API), but we re-attach them to the new P via addKid() -- both struct elements
         * and MCRs -- so the ParentTreeHandler re-registers relocated marked content. See step 4
         * for why raw K-array moves are unsafe for MCR kids.
         */
        PdfArray parentK = StructTree.normalizeKArray(parent);
        if (parentK == null) {
            return;
        }

        // 1. Re-locate the captured kids in the parent's current K array by identity;
        //    kids that earlier fixes moved elsewhere are skipped
        List<Integer> foundIndices = new ArrayList<>();
        for (PdfObject captured : kidObjects) {
            int idx = indexOfInKArray(parentK, captured);
            if (idx >= 0) {
                foundIndices.add(idx);
            } else {
                logger.debug(
                        "Captured kid no longer in K array of obj. #{}; skipping it",
                        StructTree.objNum(parent));
            }
        }
        if (foundIndices.isEmpty()) {
            return;
        }
        foundIndices.sort(null);

        // 2. Collect entries in K order, then remove in reverse order to preserve indices
        List<PdfObject> collectedObjects = new ArrayList<>();
        for (int idx : foundIndices) {
            collectedObjects.add(parentK.get(idx, false));
        }
        for (int i = foundIndices.size() - 1; i >= 0; i--) {
            parentK.remove(foundIndices.get(i));
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

        // 4. Move the collected kids into newP.
        //
        //    Struct-element kids (dicts with /S) may be relocated by a raw K-array entry plus a
        //    /P re-point: MCR registration keys off the leaf element that owns the marked content,
        //    not the container, so reparenting a struct element does not disturb the ParentTree.
        //
        //    MCR kids (bare-int MCIDs or /Type /MCR dicts) are different: they ARE the marked
        //    content, so they must go through addKid(), which re-registers them under newP via the
        //    ParentTreeHandler. A raw move leaves the page ParentTree pointing at the old parent,
        //    producing the "inconsistent ParentTree mapping" that breaks page extraction and trips
        //    Acrobat preflight.
        PdfArray newPK = new PdfArray();
        newP.getPdfObject().put(PdfName.K, newPK);

        for (PdfObject obj : collectedObjects) {
            PdfObject resolved =
                    obj.isIndirectReference() ? ((PdfIndirectReference) obj).getRefersTo() : obj;
            if (resolved instanceof PdfDictionary dict && dict.containsKey(PdfName.S)) {
                newPK.add(obj);
                dict.put(PdfName.P, newP.getPdfObject());
            } else if (resolved instanceof PdfNumber num) {
                newP.addKid(new PdfMcrNumber(num, newP));
            } else if (resolved instanceof PdfDictionary mcrDict) {
                newP.addKid(new PdfMcrDictionary(mcrDict, newP));
            } else {
                newPK.add(obj);
            }
        }

        // 5. Prune the parent if the move emptied it (e.g., a P that held only list items)
        if (parentK.isEmpty() && parent.getParent() instanceof PdfStructElem containerElem) {
            containerElem.removeKid(parent);
        }
        ListItemScribble.update(listElem);

        logger.debug(
                "Wrapped {} raw kids into L>LI>LBody>P near obj. #{} (bulletY={})",
                collectedObjects.size(),
                StructTree.objNum(parent),
                String.format("%.1f", bulletY));
    }

    /** Finds a captured kid in a K array, matching indirect references against resolved objects. */
    private static int indexOfInKArray(PdfArray kArray, PdfObject captured) {
        for (int i = 0; i < kArray.size(); i++) {
            if (StructTree.isSame(kArray.get(i, false), captured)) {
                return i;
            }
        }
        return -1;
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

        // Look for an existing L element immediately before or after the parent —
        // a run-detected head or tail of the same list may already be wrapped
        List<IStructureNode> containerKids = containerElem.getKids();
        if (parentIndex > 0
                && containerKids.get(parentIndex - 1) instanceof PdfStructElem prevElem
                && "L".equals(StructTree.mappedRole(prevElem))) {
            return prevElem;
        }
        int listIndex = parentIndex + 1;
        if (listIndex < containerKids.size()
                && containerKids.get(listIndex) instanceof PdfStructElem nextElem
                && "L".equals(StructTree.mappedRole(nextElem))) {
            return nextElem;
        }

        // No adjacent L — create one after the parent
        PdfStructElem l = new PdfStructElem(ctx.doc(), PdfName.L);
        StructTree.addKidToParent(containerElem, listIndex, l);
        return l;
    }

    /** Determines where to insert the new LI, ordered by page then bullet y-position. */
    private int findInsertPosition(PdfStructElem listElem, DocContext ctx) {
        List<PdfStructElem> existingLIs = StructTree.childrenOf(listElem, PdfStructElem.class);

        for (int i = 0; i < existingLIs.size(); i++) {
            PdfStructElem li = existingLIs.get(i);
            int liPage = StructTree.pageOf(li, ctx);
            if (liPage <= 0) {
                continue;
            }
            if (liPage > pageNum) {
                return i;
            }
            if (liPage < pageNum) {
                continue;
            }
            Rectangle liBounds = Content.getBoundsForElement(li, ctx, liPage);
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
        return "Wrapped " + kidObjects.size() + " bullet-aligned kids in LBody";
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
