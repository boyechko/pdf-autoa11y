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
package net.boyechko.pdf.autoa11y.fixes.child;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts an LBody element from under a non-LI parent (e.g., P) and places it into a proper L > LI
 * > LBody structure. Uses the /T metadata on the LBody --- bullet y-position put there by
 * BulletGlyphVisitor --- to determine insertion order within the L element.
 */
public final class ExtractLBodyToList extends TagSingleChildFix {

    private static final Logger logger = LoggerFactory.getLogger(ExtractLBodyToList.class);

    private ExtractLBodyToList(PdfStructElem kid, PdfStructElem parent) {
        super(kid, parent);
    }

    public static IssueFix tryCreate(PdfStructElem kid, PdfStructElem parent) {
        String kidRole = kid.getRole().getValue();
        String parentRole = parent.getRole().getValue();
        if ("LBody".equals(kidRole) && !"LI".equals(parentRole) && !"L".equals(parentRole)) {
            return new ExtractLBodyToList(kid, parent);
        }
        return null;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        // Find the container parent (Part/Sect/Div/Document) of the P
        IStructureNode container = parent.getParent();
        if (!(container instanceof PdfStructElem containerElem)) {
            logger.debug("Cannot extract LBody: parent of P is not a struct element");
            return;
        }

        // Find the P's index in the container's kids
        int parentIndex = StructureTree.findKidIndex(containerElem, parent);
        if (parentIndex < 0) {
            logger.debug("Cannot extract LBody: P not found in container's kids");
            return;
        }

        // Look for an existing L element immediately after the P
        List<IStructureNode> containerKids = containerElem.getKids();
        PdfStructElem listElem = null;
        int listIndex = parentIndex + 1;

        if (listIndex < containerKids.size()) {
            IStructureNode nextSibling = containerKids.get(listIndex);
            if (nextSibling instanceof PdfStructElem nextElem
                    && "L".equals(nextElem.getRole().getValue())) {
                listElem = nextElem;
            }
        }

        // If no L exists, create one and insert after P
        if (listElem == null) {
            listElem = new PdfStructElem(ctx.doc(), PdfName.L);
            StructureTree.addKidToParent(containerElem, listIndex, listElem);
        }

        // Remove the LBody from P
        parent.removeKid(kid);

        // Create LI, add to L FIRST (so LI gets /P), then add LBody to LI
        PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
        int insertPos = findInsertPosition(listElem, kid, ctx);
        listElem.addKid(insertPos, li);
        li.addKid(kid);

        // Clean up the /T metadata from LBody
        kid.getPdfObject().remove(PdfName.T);

        logger.debug(
                "Extracted LBody from obj. #{} into L obj. #{} at position {}",
                StructureTree.objNum(parent),
                StructureTree.objNum(listElem),
                insertPos);
    }

    /**
     * Determines where to insert the new LI in the L element based on visual y-position. PDF
     * y-coordinates increase upward, so higher y = earlier in document = earlier in list.
     */
    private int findInsertPosition(
            PdfStructElem listElem, PdfStructElem lBody, DocumentContext ctx) {
        // Read the bullet y-position from /T metadata
        PdfString tValue = lBody.getPdfObject().getAsString(PdfName.T);
        if (tValue == null) {
            // No metadata — append at end
            return StructureTree.structKidsOf(listElem).size();
        }

        float bulletY;
        try {
            bulletY = Float.parseFloat(tValue.getValue());
        } catch (NumberFormatException e) {
            return StructureTree.structKidsOf(listElem).size();
        }

        // Compare against bounds of existing LI elements in the L
        int pageNum = StructureTree.determinePageNumber(ctx, lBody);
        List<PdfStructElem> existingLIs = StructureTree.structKidsOf(listElem);

        for (int i = 0; i < existingLIs.size(); i++) {
            PdfStructElem existingLI = existingLIs.get(i);
            Rectangle liBounds = Content.getBoundsForElement(existingLI, ctx, pageNum);
            if (liBounds != null) {
                // Higher y = visually above = should come first
                // If our bullet is above this LI, insert before it
                float liCenterY = liBounds.getBottom() + liBounds.getHeight() / 2;
                if (bulletY > liCenterY) {
                    return i;
                }
            }
        }

        // Append at end
        return existingLIs.size();
    }

    @Override
    public String describe() {
        return "Extracted LBody from " + Format.elem(parent) + " into list";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return "Extracted LBody from " + Format.elem(parent, ctx) + " into list";
    }
}
