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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;

/** Fixes a Lbl[Figure] structure by converting the Figure to a bullet label. */
public final class TreatLblFigureAsBullet extends TagSingleChildFix {
    private TreatLblFigureAsBullet(PdfStructElem kid, PdfStructElem parent) {
        super(kid, parent);
    }

    public static IssueFix tryCreate(PdfStructElem kid, PdfStructElem parent) {
        String kidRole = kid.getRole().getValue();
        String parentRole = parent.getRole().getValue();
        if ("Lbl".equals(parentRole) && "Figure".equals(kidRole)) {
            return new TreatLblFigureAsBullet(kid, parent);
        }
        return null;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        PdfStructElem lbl = parent;
        PdfStructElem figure = kid;
        PdfStructElem li = (PdfStructElem) lbl.getParent();
        if (figure.getActualText() == null || figure.getAlt() == null) {
            // Move Figure out from under Lbl
            lbl.setRole(PdfName.Artifact);
            lbl.removeKid(figure);
            li.addKid(0, figure);

            // Remove now-empty Lbl
            li.removeKid(lbl);

            // Change Figure to Lbl
            figure.setRole(PdfName.Lbl);
            figure.setActualText(new PdfString("Bullet"));
        } else {
            throw new Exception("Figure already has ActualText or AltText");
        }
    }

    @Override
    public String describe() {
        return "Replace Lbl object #"
                + parent.getPdfObject().getIndirectReference().getObjNumber()
                + " with its Figure object #"
                + kid.getPdfObject().getIndirectReference().getObjNumber()
                + " as a bullet label";
    }

    @Override
    public String describe(DocumentContext ctx) {
        int parentObjNum = parent.getPdfObject().getIndirectReference().getObjNumber();
        int kidObjNum = kid.getPdfObject().getIndirectReference().getObjNumber();
        int pageNum = ctx.getPageNumber(parentObjNum);
        String pageInfo = (pageNum > 0) ? " (p. " + pageNum + ")" : "";

        return "Replace Lbl object #"
                + parentObjNum
                + pageInfo
                + " with its Figure object #"
                + kidObjNum
                + " as a bullet label";
    }
}
