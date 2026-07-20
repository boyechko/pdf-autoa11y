// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
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

    public static final List<String> ELEMENTS_TO_TAG =
            List.of("P", "H1", "H2", "H3", "H4", "H5", "H6");

    private final PdfStructElem element;
    private final PdfName headingLevel;

    public MistaggedHeadingFix(PdfStructElem element, PdfName headingLevel) {
        this.element = element;
        this.headingLevel = headingLevel;
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
