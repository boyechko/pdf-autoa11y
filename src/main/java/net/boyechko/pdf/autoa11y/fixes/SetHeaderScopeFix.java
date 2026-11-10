// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Gives a TH element a Table-owned {@code /Scope}. */
public class SetHeaderScopeFix implements IssueFix {
    private final PdfStructElem header;
    private final PdfName scope;

    public SetHeaderScopeFix(PdfStructElem header, PdfName scope) {
        this.header = header;
        this.scope = scope;
    }

    @Override
    public void apply(DocContext ctx) {
        StructTree.setAttributeValue(header, PdfName.Table, PdfName.Scope, scope);
    }

    @Override
    public String describe() {
        return "Set TH scope to " + scope.getValue();
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(Locus.atElem(ctx, header));
    }

    @Override
    public String groupLabel() {
        return "Table header scopes";
    }
}
