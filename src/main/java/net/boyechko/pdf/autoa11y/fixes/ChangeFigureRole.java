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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/** Changes the role of a Figure element to a specified role. */
public class ChangeFigureRole implements IssueFix {
    private static final int P_STRUCTURE = 20;

    private final PdfStructElem figure;
    private final PdfName newRole;

    public ChangeFigureRole(PdfStructElem figure, PdfName newRole) {
        this.figure = figure;
        this.newRole = newRole;
    }

    @Override
    public int priority() {
        return P_STRUCTURE;
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
