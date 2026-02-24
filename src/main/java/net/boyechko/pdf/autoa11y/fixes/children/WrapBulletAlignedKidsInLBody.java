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
package net.boyechko.pdf.autoa11y.fixes.children;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a group of consecutive raw kids (MCRs and struct elements) inside a parent element into an
 * LBody > P structure. The bullet y-position is stored as /T on the LBody so that
 * ExtractLBodyToList can determine visual ordering when building the final L > LI structure.
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
    public void apply(DocumentContext ctx) throws Exception {
        /*
         * For moving MCRs between parents, raw K-array manipulation is
         * unavoidable (iText has no MCR-move API). But for struct element
         * insertion, always use addKid() to ensure proper indirect reference
         * handling. The hybrid approach works: remove items from the K array
         * manually, but insert new struct elements via addKid.
         *
         */
        PdfArray parentK = StructureTree.normalizeKArray(parent);
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
        int insertIndex = kidIndices.get(0);
        for (int i = kidIndices.size() - 1; i >= 0; i--) {
            int idx = kidIndices.get(i);
            if (idx < parentK.size()) {
                parentK.remove(idx);
            }
        }

        // 3. Create LBody and insert into parent FIRST (so it gets /P)
        PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        lBody.getPdfObject().put(PdfName.T, new PdfString(String.valueOf(bulletY)));
        if (insertIndex > parentK.size()) {
            insertIndex = parentK.size();
        }
        parent.addKid(insertIndex, lBody);

        // 4. Create P and add to LBody (lBody now has /P, so this works)
        PdfStructElem newP = new PdfStructElem(ctx.doc(), PdfName.P);
        if (parent.getPdfObject().containsKey(PdfName.Pg)) {
            newP.getPdfObject().put(PdfName.Pg, parent.getPdfObject().get(PdfName.Pg));
        }
        lBody.addKid(newP);

        // 5. Build newP's K array with the collected objects
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
                "Wrapped {} raw kids in LBody>P under obj. #{} (bulletY={})",
                kidIndices.size(),
                StructureTree.objNum(parent),
                String.format("%.1f", bulletY));
    }

    @Override
    public String describe() {
        return "Wrapped "
                + kidIndices.size()
                + " bullet-aligned kids in LBody under "
                + Format.elem(parent);
    }

    @Override
    public String describe(DocumentContext ctx) {
        return "Wrapped "
                + kidIndices.size()
                + " bullet-aligned kids in LBody under "
                + Format.elem(parent, ctx);
    }
}
