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
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Changes a P element to a Lbl in an LI structure. */
public final class ChangePToLblInLI extends TagMultipleChildrenFix {
    private ChangePToLblInLI(PdfStructElem parent, List<PdfStructElem> kids) {
        super(parent, kids);
    }

    public static IssueFix tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
        String parentRole = parent.getRole().getValue();
        // There should be exactly two kids, one of which is LBody and the other P
        if ("LI".equals(parentRole) && kids.size() == 2) {
            String kid1Role = kids.get(0).getRole().getValue();
            String kid2Role = kids.get(1).getRole().getValue();

            if (("P".equals(kid1Role) && "LBody".equals(kid2Role))
                    || ("LBody".equals(kid1Role) && "P".equals(kid2Role))) {
                return new ChangePToLblInLI(parent, kids);
            }
        }
        return null;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        for (PdfStructElem p : kids) {
            if ("P".equals(p.getRole().getValue())) {
                p.setRole(PdfName.Lbl);
            }
        }
    }

    @Override
    public String describe() {
        return "Changed P to Lbl in " + Format.elem(parent);
    }

    @Override
    public String describe(DocumentContext ctx) {
        return "Changed P to Lbl in " + Format.elem(parent, ctx);
    }
}
