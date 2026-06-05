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
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/** Retags a P or H1-H6 element as a heading of an appropriate level. */
public class MistaggedHeadingFix implements IssueFix {
    private static final int P_STRUCTURE = 20;

    public static final List<String> ELEMENTS_TO_TAG =
            List.of("P", "H1", "H2", "H3", "H4", "H5", "H6");

    private final PdfStructElem element;
    private final PdfName headingLevel;

    public MistaggedHeadingFix(PdfStructElem element, PdfName headingLevel) {
        this.element = element;
        this.headingLevel = headingLevel;
    }

    @Override
    public int priority() {
        return P_STRUCTURE;
    }

    @Override
    public void apply(DocContext ctx) {
        if (!ELEMENTS_TO_TAG.contains(StructTree.mappedRole(element))) {
            return;
        }
        StructTree.setToolScribble(element, "!SET_ROLE " + headingLevel.getValue());
    }

    @Override
    public String describe() {
        return "Marked for retagging as " + headingLevel.getValue();
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "Elements marked for retagging as headings";
    }
}
