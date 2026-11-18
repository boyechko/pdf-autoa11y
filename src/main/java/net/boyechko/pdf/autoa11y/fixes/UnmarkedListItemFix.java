// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue.Scribble;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/** Marks a list item the page never bulleted or numbered for breaking out of its list. */
public class UnmarkedListItemFix implements IssueFix {

    private static final String BREAK_OUT = Scribble.INSTRUCTION_MARK + "BREAK_OUT";

    private final PdfStructElem item;

    public UnmarkedListItemFix(PdfStructElem item) {
        this.item = item;
    }

    @Override
    public void apply(DocContext ctx) {
        Tag.of(item).stamp(BREAK_OUT);
    }

    @Override
    public String describe() {
        return "Marked for breaking out of its list";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), Locus.atElem(ctx, item));
    }

    @Override
    public String groupLabel() {
        return "List items marked for breaking out";
    }
}
