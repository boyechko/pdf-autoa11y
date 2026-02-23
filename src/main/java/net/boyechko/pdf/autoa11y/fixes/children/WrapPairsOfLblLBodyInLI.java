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
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;

/** Wraps pairs of Lbl,LBody elements in an LI structure. */
public final class WrapPairsOfLblLBodyInLI extends TagMultipleChildrenFix {
    private WrapPairsOfLblLBodyInLI(PdfStructElem parent, List<PdfStructElem> kids) {
        super(parent, kids);
    }

    public static IssueFix tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
        String parentRole = parent.getRole().getValue();

        // Check if parent is L and we have alternating Lbl/LBody pattern
        if ("L".equals(parentRole) && kids.size() >= 2 && kids.size() % 2 == 0) {
            // Verify alternating Lbl/LBody pattern
            for (int i = 0; i < kids.size(); i += 2) {
                String lblRole = kids.get(i).getRole().getValue();
                String lBodyRole = kids.get(i + 1).getRole().getValue();

                if (!"Lbl".equals(lblRole) || !"LBody".equals(lBodyRole)) {
                    return null;
                }
            }
            return new WrapPairsOfLblLBodyInLI(parent, kids);
        }
        return null;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        logger.debug(
                "Applying WrapPairsOfLblLBodyInLI to L obj. #"
                        + StructureTree.objNum(parent)
                        + " with "
                        + kids.size()
                        + " kids");
        for (int i = 0; i < kids.size(); i += 2) {
            PdfStructElem lbl = kids.get(i);
            PdfStructElem lBody = kids.get(i + 1);

            PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
            parent.addKid(newLI);

            parent.removeKid(lbl);
            newLI.addKid(lbl);

            parent.removeKid(lBody);
            newLI.addKid(lBody);
        }
    }

    @Override
    public String describe() {
        return "Wrapped pairs of Lbl/LBody in LI elements for " + Format.elem(parent);
    }

    @Override
    public String describe(DocumentContext ctx) {
        return "Wrapped pairs of Lbl/LBody in LI elements for " + Format.elem(parent, ctx);
    }
}
