// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructNode;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Removes a single orphaned MCR/OBJR from its parent, then prunes any ancestor that becomes empty
 * as a result.
 */
public class OrphanedContentFix implements IssueFix {

    private final PdfStructElem parent;
    private final PdfMcr orphan;
    private int prunedCount;

    public OrphanedContentFix(PdfStructElem parent, PdfMcr orphan) {
        this.parent = parent;
        this.orphan = orphan;
    }

    @Override
    public void apply(DocContext ctx) {
        StructNode content = StructNode.of(orphan);
        if (!Tag.of(parent).kids().contains(content)) return;

        content.remove();
        prunedCount = 1 + Tag.of(parent).pruneIfEmpty();
    }

    @Override
    public String describe() {
        return "Removed orphaned " + typeOf(orphan);
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), Locus.atElem(ctx, parent));
    }

    @Override
    public String groupLabel() {
        return "orphaned MCRs/OBJRs removed";
    }

    @Override
    public int resolvedItemCount() {
        return prunedCount;
    }

    private static String typeOf(PdfMcr mcr) {
        return mcr instanceof PdfObjRef ? "OBJR" : "MCID " + mcr.getMcid();
    }
}
