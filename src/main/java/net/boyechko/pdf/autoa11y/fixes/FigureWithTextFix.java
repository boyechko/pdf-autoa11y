// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/** Changes the role of a Figure element to a specified role. */
public class FigureWithTextFix implements IssueFix {

    private final PdfStructElem figure;
    private final PdfName newRole;

    public FigureWithTextFix(PdfStructElem figure, PdfName newRole) {
        this.figure = figure;
        this.newRole = newRole;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (!PdfName.Figure.equals(figure.getRole())) {
            return;
        }
        figure.setRole(newRole);
    }

    @Override
    public String describe() {
        return "Changed Figure to " + newRole.getValue();
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), IssueLoc.atElem(ctx, figure));
    }

    @Override
    public String groupLabel() {
        return "Figure roles changed";
    }
}
