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
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;

/** Wraps pairs of Lbl,P elements in an LI structure. */
public final class WrapPairsOfLblPInLI extends TagMultipleChildrenFix {
    private WrapPairsOfLblPInLI(PdfStructElem parent, List<PdfStructElem> kids) {
        super(parent, kids);
    }

    public static IssueFix tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
        String parentRole = parent.getRole().getValue();

        // Check if parent is L and we have alternating Lbl/P pattern
        if ("L".equals(parentRole) && kids.size() >= 2 && kids.size() % 2 == 0) {
            // Verify alternating Lbl/P pattern
            for (int i = 0; i < kids.size(); i += 2) {
                String lblRole = kids.get(i).getRole().getValue();
                String pRole = kids.get(i + 1).getRole().getValue();

                if (!"Lbl".equals(lblRole) || !"P".equals(pRole)) {
                    return null;
                }
            }
            return new WrapPairsOfLblPInLI(parent, kids);
        }
        return null;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        logger.debug(
                "Applying WrapPairsOfLblPInLI to L obj #{} (p. {}) with {} kids",
                StructureTree.objNumber(parent),
                kids.size());
        for (int i = 0; i < kids.size(); i += 2) {
            PdfStructElem lbl = kids.get(i);
            PdfStructElem p = kids.get(i + 1);

            PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
            parent.addKid(newLI);

            // Move the Lbl directly under LI
            parent.removeKid(lbl);
            newLI.addKid(lbl);

            // Create LBody and move P under it
            PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
            newLI.addKid(newLBody);
            parent.removeKid(p);
            newLBody.addKid(p);
        }
    }

    @Override
    public String describe() {
        int objNum = StructureTree.objNumber(parent);
        return "Wrapped pairs of Lbl/P in LI elements for L obj #" + objNum;
    }

    @Override
    public String describe(DocumentContext ctx) {
        int objNum = StructureTree.objNumber(parent);
        int pageNum = ctx.getPageNumber(objNum);
        String pageInfo = (pageNum > 0) ? " (p. " + pageNum + ")" : "";
        return "Wrapped pairs of Lbl/P in LI elements for L obj #" + objNum + pageInfo;
    }
}
