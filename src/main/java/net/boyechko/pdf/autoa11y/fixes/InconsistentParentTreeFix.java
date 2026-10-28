// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/**
 * Makes the page ParentTree agree with the live structure tree. iText seeds its marked-content
 * registry from the elements the <em>stored</em> ParentTree names, so two kinds of drift survive a
 * save: elements no longer reachable from the root (e.g. a structure tree left behind by
 * re-tagging) keep claiming MCIDs, and live MCRs whose MCID they hold stay unregistered. The fix
 * first drops the detached owners' MCRs, then re-registers every live MCR the registry does not
 * resolve back to its element. The order matters: iText unregisters by (page, MCID), so dropping a
 * detached claim can evict a live registration that the second pass restores.
 */
public class InconsistentParentTreeFix implements IssueFix {

    private int droppedCount;
    private int repairedCount;

    @Override
    public void apply(DocContext ctx) {
        PdfDocument doc = ctx.doc();
        PdfStructTreeRoot root = doc.getStructTreeRoot();
        List<PdfStructElem> live = liveElements(root);

        for (PdfStructElem owner : detachedOwners(doc, live)) {
            droppedCount += dropRegisteredMcrs(owner);
        }
        for (PdfStructElem element : live) {
            repairedCount += reregister(root, element);
        }
    }

    /** Returns every structure element reachable from the root, ignoring verification marks. */
    public static List<PdfStructElem> liveElements(PdfStructTreeRoot root) {
        List<PdfStructElem> result = new ArrayList<>();
        collectElements(root, result);
        return result;
    }

    /** Returns the elements holding ParentTree registrations but unreachable from the root. */
    public static Collection<PdfStructElem> detachedOwners(
            PdfDocument doc, List<PdfStructElem> live) {
        Set<PdfIndirectReference> liveRefs = new LinkedHashSet<>();
        for (PdfStructElem elem : live) liveRefs.add(elem.getPdfObject().getIndirectReference());

        Map<PdfIndirectReference, PdfStructElem> detached = new LinkedHashMap<>();
        PdfStructTreeRoot root = doc.getStructTreeRoot();
        for (int pageNum = 1; pageNum <= doc.getNumberOfPages(); pageNum++) {
            Collection<PdfMcr> mcrs = root.getPageMarkedContentReferences(doc.getPage(pageNum));
            if (mcrs == null) continue;
            for (PdfMcr mcr : mcrs) {
                if (mcr.getParent() instanceof PdfStructElem owner) {
                    PdfIndirectReference ref = owner.getPdfObject().getIndirectReference();
                    if (!liveRefs.contains(ref)) detached.putIfAbsent(ref, owner);
                }
            }
        }
        return detached.values();
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

    private static void collectElements(IStructureNode node, List<PdfStructElem> result) {
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                result.add(elem);
                collectElements(elem, result);
            }
        }
    }

    /** Removes {@code owner}'s page-bound MCR kids, which unregisters them; returns the count. */
    private static int dropRegisteredMcrs(PdfStructElem owner) {
        List<IStructureNode> kids = owner.getKids();
        int dropped = 0;
        for (int i = kids.size() - 1; i >= 0; i--) {
            // Pageless MCRs were never registered, and unregistering them can NPE
            if (kids.get(i) instanceof PdfMcr mcr && mcr.getPageIndirectReference() != null) {
                owner.removeKid(i);
                dropped++;
            }
        }
        return dropped;
    }

    /**
     * Re-adds {@code element}'s unresolved MCR kids so they register under it; returns the count.
     */
    private static int reregister(PdfStructTreeRoot root, PdfStructElem element) {
        // Collect up front; re-adding mutates /K, so don't iterate the live kid list
        List<PdfMcr> toRepair = new ArrayList<>();
        List<IStructureNode> kids = element.getKids();
        if (kids == null) return 0;
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
        }
        return toRepair.size();
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
        return "Re-registered "
                + repairedCount
                + " marked-content reference(s) in the ParentTree; dropped "
                + droppedCount
                + " held by detached structure elements";
    }

    @Override
    public String groupLabel() {
        return "inconsistent ParentTree mappings repaired";
    }

    @Override
    public int resolvedItemCount() {
        return repairedCount + droppedCount;
    }
}
