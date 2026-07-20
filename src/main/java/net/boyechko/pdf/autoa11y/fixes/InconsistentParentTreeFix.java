// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Repairs a structure element whose marked-content kids are not (or wrongly) reflected in the page
 * ParentTree. Each such MCR is removed from the element's /K and re-added via {@link
 * PdfStructElem#addKid}, which routes through the ParentTreeHandler and rebuilds the reverse index
 * so the ParentTree is rewritten consistently on save.
 */
public class InconsistentParentTreeFix implements IssueFix {

    private final PdfStructElem element;
    private int repairedCount;

    public InconsistentParentTreeFix(PdfStructElem element) {
        this.element = element;
    }

    @Override
    public void apply(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();

        // Collect the MCR kids needing re-registration up front; re-adding mutates /K, so we
        // must not iterate the live kid list while changing it.
        List<PdfMcr> toRepair = new ArrayList<>();
        List<IStructureNode> kids = element.getKids();
        if (kids == null) return;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfMcr mcr && needsReregistration(root, element, mcr)) {
                toRepair.add(mcr);
            }
        }

        for (PdfMcr mcr : toRepair) {
            int idx = indexOfKid(element, mcr);
            if (idx < 0) continue;
            element.removeKid(
                    idx, true); // drop from /K without unregistering (it isn't registered)
            element.addKid(idx, freshWrapper(mcr, element)); // re-add + register under this element
            repairedCount++;
        }
    }

    /**
     * Returns whether the page ParentTree fails to resolve {@code mcr} back to {@code owner} --
     * i.e. the reverse index is missing the entry or points at a different element.
     */
    public static boolean needsReregistration(
            PdfStructTreeRoot root, PdfStructElem owner, PdfMcr mcr) {
        if (mcr.getPageIndirectReference() == null) {
            return false; // orphaned MCR (no resolvable page) -- OrphanedContentCheck's domain
        }
        if (mcr.getMcid() < 0) {
            return false; // object references (OBJR) are keyed differently; leave them alone
        }
        PdfMcr registered = root.findMcrByMcid(mcr.getPageObject(), mcr.getMcid());
        if (registered == null) {
            return true;
        }
        IStructureNode registeredParent = registered.getParent();
        return !(registeredParent instanceof PdfStructElem parentElem)
                || !StructTree.isSameElement(parentElem, owner);
    }

    private static PdfMcr freshWrapper(PdfMcr mcr, PdfStructElem parent) {
        PdfObject underlying = mcr.getPdfObject();
        if (underlying instanceof PdfNumber num) {
            return new PdfMcrNumber(num, parent);
        }
        return new PdfMcrDictionary((PdfDictionary) underlying, parent);
    }

    private static int indexOfKid(PdfStructElem element, PdfMcr mcr) {
        List<IStructureNode> kids = element.getKids();
        if (kids == null) return -1;
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i) instanceof PdfMcr candidate
                    && StructTree.isSame(candidate.getPdfObject(), mcr.getPdfObject())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String describe() {
        return "Re-registered " + repairedCount + " marked-content reference(s) in the ParentTree";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "inconsistent ParentTree mappings repaired";
    }

    @Override
    public int resolvedItemCount() {
        return repairedCount;
    }
}
