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
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Wraps a single child element under LI in a LBody structure. */
public final class WrapInLBody extends TagSingleChildFix {
    private static final List<String> validKidRoles = List.of("Div", "Figure", "P", "Span");

    private WrapInLBody(PdfStructElem kid, PdfStructElem parent) {
        super(kid, parent);
    }

    public static IssueFix tryCreate(PdfStructElem kid, PdfStructElem parent) {
        String kidRole = kid.getRole().getValue();
        String parentRole = parent.getRole().getValue();
        if ("LI".equals(parentRole) && validKidRoles.contains(kidRole)) {
            return new WrapInLBody(kid, parent);
        }
        return null;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
        parent.addKid(newLBody);
        parent.removeKid(kid);
        newLBody.addKid(kid);
    }

    @Override
    public String describe() {
        return "Wrapped " + getKidRole() + " in LBody";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
