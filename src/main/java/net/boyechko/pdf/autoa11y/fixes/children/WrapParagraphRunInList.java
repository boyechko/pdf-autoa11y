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

        // Find the position of the first P among the parent's kids (MCRs + struct elements)
        PdfStructElem firstP = kids.get(0);
        List<IStructureNode> parentKids = parent.getKids();
        int insertIndex = -1;
        for (int i = 0; i < parentKids.size(); i++) {
            IStructureNode kid = parentKids.get(i);
            if (kid instanceof PdfStructElem elem && StructureTree.isSameElement(elem, firstP)) {
                insertIndex = i;
                break;
            }
        }
        if (insertIndex < 0) {
            throw new Exception("First P element not found in parent kids");
        }

        // Remove all P elements from parent first
        for (PdfStructElem p : kids) {
            parent.removeKid(p);
        }

        // Create L element and add it at the saved position
        PdfStructElem listElem = new PdfStructElem(ctx.doc(), PdfName.L);
        parent.addKid(insertIndex, listElem);

        // Build LI > LBody > P structure under L
        for (PdfStructElem p : kids) {
            PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
            PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);

            listElem.addKid(li);
            li.addKid(lBody);
            lBody.addKid(p);
        }

        logger.debug(
                "Wrapped {} P elements in L > LI > LBody under obj #{}",
                kids.size(),
                StructureTree.objNumber(parent));
    }

    @Override
    public String describe() {
        return "Retagged suspected list of " + kids.size() + " P elements as a list";
    }

    @Override
    public String describe(DocumentContext ctx) {
        int objNum = StructureTree.objNumber(parent);
        int pageNum = ctx.getPageNumber(objNum);
        return String.format(
                "Retagged suspected list of %d P elements as a list (obj #%d, p. %d)",
                kids.size(), objNum, pageNum);
    }
}
