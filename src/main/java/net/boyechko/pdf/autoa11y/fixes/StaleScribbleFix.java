/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/** Clears the /T key from a structure element that contains a stale workflow scribble. */
public class StaleScribbleFix implements IssueFix {
    private static final int P_CLEAR_SCRIBBLE = 50;

    private final PdfStructElem element;
    private final String scribble;

    public StaleScribbleFix(PdfStructElem element, String scribble) {
        this.element = element;
        this.scribble = scribble;
    }

    @Override
    public int priority() {
        return P_CLEAR_SCRIBBLE;
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
