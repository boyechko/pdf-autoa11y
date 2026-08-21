// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Converts a P element containing only links to an L element. */
public final class ParagraphOfLinksFix implements IssueFix {

    protected final PdfStructElem parent;
    protected final List<PdfStructElem> kids;

    public ParagraphOfLinksFix(PdfStructElem parent, List<PdfStructElem> kids) {
        this.parent = parent;
        this.kids =
                kids != null
                        ? kids.stream().map(kid -> (PdfStructElem) kid).collect(Collectors.toList())
                        : List.of();
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        parent.setRole(PdfName.L);
        ListAssembler assembler = new ListAssembler(ctx.doc());
        for (PdfStructElem kid : kids) {
            assembler.newItemBody(parent).addKid(kid);
            parent.removeKid(kid);
        }
        ListItemScribble.update(parent, "P of links, ");
    }

    @Override
    public int priority() {
        return 10; // list-creation phase: before LBody wraps (20) and merges (30)
    }

    @Override
    public String describe() {
        return "Changed paragraph of links into a list";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
