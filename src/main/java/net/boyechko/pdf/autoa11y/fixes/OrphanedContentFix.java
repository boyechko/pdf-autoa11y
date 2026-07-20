// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
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
        // Locate the orphan in parent's /K so we can remove it by index. We
        // avoid parent.removeKid(IStructureNode), which routes through
        // ParentTreeHandler.unregisterMcr -> PdfMcr.getPageObject(), and
        // getPageObject() NPEs when getPageIndirectReference() is null.
        // removeKid(index, prepareForReAdding=true) skips the unregister step,
        // which is safe for orphans since they were never registered in the
        // ParentTree to begin with.
        PdfArray kArray = StructTree.normalizeKArray(parent);
        if (kArray == null) return;

        PdfObject orphanObj = orphan.getPdfObject();
        int idx = -1;
        for (int i = 0; i < kArray.size(); i++) {
            if (StructTree.isSame(kArray.get(i), orphanObj)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        parent.removeKid(idx, true);
        prunedCount = 1 + StructTree.pruneEmpty(parent);
    }

    @Override
    public String describe() {
        return "Removed orphaned " + typeOf(orphan);
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, parent));
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
