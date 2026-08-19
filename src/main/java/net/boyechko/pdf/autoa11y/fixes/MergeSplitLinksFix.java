// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.Link;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges Link tags that are two halves (or more) of one logical link into a single {@code <Link>}:
 * the later tags' OBJRs and content move into the first tag, and the emptied tags are removed. Both
 * annotations are kept — a single Link element may enclose several link annotations as long as they
 * share an action — so no annotation geometry is touched.
 *
 * <p>The merged element's OBJRs are grouped ahead of its content, which the PDF Association's
 * Tagged PDF Best Practice Guide recommends for links whose annotations span a page break.
 *
 * <p>Applies only while the Link tags are still immediate siblings sharing one destination, so a
 * merge invalidated by an earlier fix is skipped rather than forced. The surviving Link is
 * tool-stamped {@code LINKS MERGED} so the sites are findable in {@code --dump-tree}.
 */
public final class MergeSplitLinksFix implements IssueFix {

    private static final Logger logger = LoggerFactory.getLogger(MergeSplitLinksFix.class);

    private static final String MERGED_SCRIBBLE = "LINKS MERGED";

    private final List<PdfStructElem> links;
    private int mergedCount;

    public MergeSplitLinksFix(List<PdfStructElem> links) {
        this.links = List.copyOf(links);
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (links.size() < 2 || !(links.get(0).getParent() instanceof PdfStructElem parent)) {
            return;
        }
        if (!areAdjacentKidsOf(parent) || !Link.allShareOneDestination(links)) {
            logger.debug(
                    "Links #{} and following are no longer one split link; skipping merge",
                    StructTree.objNum(links.get(0)));
            return;
        }

        PdfStructElem target = links.get(0);
        for (PdfStructElem later : links.subList(1, links.size())) {
            for (IStructureNode kid : new ArrayList<>(later.getKids())) {
                StructTree.moveKid(kid, later, target);
            }
            parent.removeKid(later);
            mergedCount++;
        }
        groupObjRefsFirst(target);
        markAsMerged(target);

        logger.debug("Merged {} Link tags into #{}", links.size(), StructTree.objNum(target));
    }

    /**
     * Tool-stamps the surviving Link, preserving any existing scribble and its authorship, and
     * skipping elements already carrying the segment so re-runs stay idempotent.
     */
    private static void markAsMerged(PdfStructElem elem) {
        DocValue.Scribble existing = StructTree.getScribble(elem);
        if (existing == null) {
            StructTree.setToolScribble(elem, MERGED_SCRIBBLE);
        } else if (existing.segments().stream()
                .noneMatch(seg -> seg.trim().equals(MERGED_SCRIBBLE))) {
            StructTree.addScribble(elem, MERGED_SCRIBBLE);
        }
    }

    /** True if the links are still consecutive kids of the parent, in the recorded order. */
    private boolean areAdjacentKidsOf(PdfStructElem parent) {
        int first = StructTree.findKidIndex(parent, links.get(0));
        if (first < 0) {
            return false;
        }
        List<IStructureNode> kids = parent.getKids();
        if (first + links.size() > kids.size()) {
            return false;
        }
        for (int i = 1; i < links.size(); i++) {
            if (!(kids.get(first + i) instanceof PdfStructElem sibling)
                    || !StructTree.isSameElement(sibling, links.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves the element's OBJR entries ahead of its content in the /K array, preserving the
     * relative order within each group. Positions within /K do not affect the ParentTree, which
     * keys on MCID and /StructParent, so this is a pure reading-order edit.
     */
    private static void groupObjRefsFirst(PdfStructElem elem) {
        PdfArray kArray = StructTree.normalizeKArray(elem);
        if (kArray == null) {
            return;
        }
        List<PdfObject> objRefs = new ArrayList<>();
        List<PdfObject> content = new ArrayList<>();
        for (int i = 0; i < kArray.size(); i++) {
            PdfObject entry = kArray.get(i, false);
            (isObjRef(entry) ? objRefs : content).add(entry);
        }
        if (objRefs.isEmpty() || content.isEmpty()) {
            return;
        }
        int index = 0;
        for (PdfObject entry : objRefs) {
            kArray.set(index++, entry);
        }
        for (PdfObject entry : content) {
            kArray.set(index++, entry);
        }
        elem.setModified();
    }

    private static boolean isObjRef(PdfObject entry) {
        PdfObject resolved = entry instanceof PdfIndirectReference ref ? ref.getRefersTo() : entry;
        return resolved instanceof PdfDictionary dict
                && PdfName.OBJR.equals(dict.getAsName(PdfName.Type));
    }

    @Override
    public String describe() {
        return "Merged " + (mergedCount + 1) + " Link tags into one";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, links.get(0)));
    }

    @Override
    public String groupLabel() {
        return "Merged split links";
    }

    @Override
    public int resolvedItemCount() {
        return mergedCount;
    }
}
