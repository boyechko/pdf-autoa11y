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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructureTree;

/** Wraps a run of consecutive P elements in L > LI > LBody structure. */
public final class WrapParagraphRunInList extends TagMultipleChildrenFix {

    public WrapParagraphRunInList(PdfStructElem parent, List<PdfStructElem> kids) {
        super(parent, kids);
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        if (kids.isEmpty()) {
            return;
        }

        PdfStructElem firstP = kids.get(0);
        PdfStructElem actualParent = findParentContaining(firstP);
        if (actualParent == null) {
            logger.debug(
                    "Skipping fix: P element obj. #{} not found in any ancestor's K array "
                            + "(stored parent was obj. #{})",
                    StructureTree.objNum(firstP),
                    StructureTree.objNum(parent));
            return;
        }

        // Find the position of the first P among the actual parent's kids
        List<IStructureNode> parentKids = actualParent.getKids();
        int insertIndex = -1;
        for (int i = 0; i < parentKids.size(); i++) {
            IStructureNode kid = parentKids.get(i);
            if (kid instanceof PdfStructElem elem && StructureTree.isSameElement(elem, firstP)) {
                insertIndex = i;
                break;
            }
        }
        if (insertIndex < 0) {
            return;
        }

        // Remove all P elements from actual parent
        for (PdfStructElem p : kids) {
            actualParent.removeKid(p);
        }

        // Create L element and add it at the saved position
        PdfStructElem listElem = new PdfStructElem(ctx.doc(), PdfName.L);
        actualParent.addKid(insertIndex, listElem);

        // Build LI > LBody > P structure under L
        for (PdfStructElem p : kids) {
            PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
            PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);

            listElem.addKid(li);
            li.addKid(lBody);
            lBody.addKid(p);
        }

        logger.debug(
                "Wrapped {} P elements in L > LI > LBody under obj. #{}",
                kids.size(),
                StructureTree.objNum(actualParent));
    }

    /**
     * Finds the actual parent whose K array contains the given element. Starts from the element's
     * /P parent and walks up the ancestor chain. This handles cases where FlattenNesting promoted
     * children but iText's getKids() cache became stale.
     */
    private PdfStructElem findParentContaining(PdfStructElem target) {
        // Start from the target's /P parent, then walk up ancestors
        IStructureNode candidate = target.getParent();
        for (int depth = 0; depth < 10 && candidate instanceof PdfStructElem elem; depth++) {
            for (IStructureNode kid : elem.getKids()) {
                if (kid instanceof PdfStructElem kidElem
                        && StructureTree.isSameElement(kidElem, target)) {
                    return elem;
                }
            }
            candidate = elem.getParent();
        }
        return null;
    }

    @Override
    public String describe() {
        return "Retagged suspected list of " + kids.size() + " P elements as a list";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return "Retagged suspected list of "
                + kids.size()
                + " P elements as a list "
                + Format.elem(parent, ctx);
    }
}
