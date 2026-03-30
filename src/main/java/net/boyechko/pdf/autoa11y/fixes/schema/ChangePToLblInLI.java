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
package net.boyechko.pdf.autoa11y.fixes.schema;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Changes a P element to a Lbl in an LI structure. */
public final class ChangePToLblInLI extends SchemaChildrenFix {
    private ChangePToLblInLI(PdfStructElem parent, List<PdfStructElem> kids) {
        super(parent, kids);
    }

    public static IssueFix tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
        String parentRole = StructTree.mappedRole(parent);
        // There should be exactly two kids, one of which is LBody and the other P
        if ("LI".equals(parentRole) && kids.size() == 2) {
            String kid1Role = StructTree.mappedRole(kids.get(0));
            String kid2Role = StructTree.mappedRole(kids.get(1));

            if (("P".equals(kid1Role) && "LBody".equals(kid2Role))
                    || ("LBody".equals(kid1Role) && "P".equals(kid2Role))) {
                return new ChangePToLblInLI(parent, kids);
            }
        }
        return null;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        for (PdfStructElem p : kids) {
            if ("P".equals(StructTree.mappedRole(p))) {
                p.setRole(PdfName.Lbl);
            }
        }
    }

    @Override
    public String describe() {
        return "Changed P to Lbl in LI";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
