// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/** Clears the /T key from a structure element that contains a stale workflow scribble. */
public class StaleScribbleFix implements IssueFix {

    private final PdfStructElem element;
    private final String scribble;

    public StaleScribbleFix(PdfStructElem element, String scribble) {
        this.element = element;
        this.scribble = scribble;
    }

    @Override
    public void apply(DocContext ctx) {
        element.getPdfObject().remove(PdfName.T);
    }

    @Override
    public String describe() {
        return "Cleared stale scribble: " + scribble;
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "Stale scribble cleanup";
    }
}
