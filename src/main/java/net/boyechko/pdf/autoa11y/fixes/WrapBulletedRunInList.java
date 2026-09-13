// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a run of consecutive bulleted siblings in one L &gt; LI &gt; LBody list.
 *
 * <p>A run may open partway through its first sibling: an element that sets a paragraph of prose
 * and then the run's opening item on its last line contributes only that line, whose kids are
 * carved out into the item and the prose left where it stands. Every other member of the run is one
 * item whole.
 *
 * <p>With a {@code nestInto} target the list becomes a sublist, appended inside that list's last LI
 * &gt; LBody rather than standing where the run did.
 */
public final class WrapBulletedRunInList implements IssueFix {

    private static final Logger logger = LoggerFactory.getLogger(WrapBulletedRunInList.class);

    private final PdfStructElem container;
    private final PdfStructElem carveFrom;
    private final List<PdfObject> carvedKids;
    private final int carvedPage;
    private final List<PdfStructElem> wholeItems;
    private final PdfStructElem nestInto;

    /** Wraps whole elements only. */
    public WrapBulletedRunInList(PdfStructElem container, List<PdfStructElem> wholeItems) {
        this(container, null, List.of(), 0, wholeItems, null);
    }

    /** Wraps whole elements as a sublist of {@code nestInto}. */
    public WrapBulletedRunInList(
            PdfStructElem container, List<PdfStructElem> wholeItems, PdfStructElem nestInto) {
        this(container, null, List.of(), 0, wholeItems, nestInto);
    }

    /**
     * @param carveFrom element whose last line opens the run, or null when none does
     * @param carvedKids that element's kids on the bulleted line, which become the first item
     * @param carvedPage page the carved kids sit on, for the new item's /Pg
     */
    public WrapBulletedRunInList(
            PdfStructElem container,
            PdfStructElem carveFrom,
            List<PdfObject> carvedKids,
            int carvedPage,
            List<PdfStructElem> wholeItems,
            PdfStructElem nestInto) {
        this.container = container;
        this.nestInto = nestInto;
        this.carveFrom = carveFrom;
        this.carvedKids = List.copyOf(carvedKids);
        this.carvedPage = carvedPage;
        this.wholeItems = List.copyOf(wholeItems);
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfStructElem anchor = carveFrom != null ? carveFrom : firstOrNull(wholeItems);
        if (anchor == null) {
            return;
        }
        PdfStructElem parent = parentContaining(anchor);
        if (parent == null) {
            logger.debug(
                    "Skipping fix: obj. #{} not found in any ancestor's K array "
                            + "(stored container was obj. #{})",
                    StructTree.objNum(anchor),
                    StructTree.objNum(container));
            return;
        }

        // The list takes the run's place: after the element it was carved out of, since that
        // element keeps its prose, and otherwise at the first whole item's own position.
        int anchorIndex = indexOfKid(parent, anchor);
        if (anchorIndex < 0) {
            return;
        }
        int insertIndex = carveFrom != null ? anchorIndex + 1 : anchorIndex;

        List<PdfObject> carved = detachCarvedKids();
        for (PdfStructElem item : wholeItems) {
            parent.removeKid(item);
        }

        ListAssembler assembler = new ListAssembler(ctx.doc());
        PdfStructElem sublistHost = sublistHost();
        PdfStructElem list =
                sublistHost != null
                        ? assembler.newListIn(sublistHost)
                        : assembler.newListAt(parent, insertIndex);
        if (!carved.isEmpty()) {
            adoptCarvedKids(ctx, assembler, list, carved);
        }
        for (PdfStructElem item : wholeItems) {
            assembler.newItemBody(list).addKid(item);
        }
        pruneIfEmptied(parent);
        ListItemScribble.update(list, "BULLETS " + itemCount());

        logger.debug(
                "Wrapped {} bulleted items in L > LI > LBody under obj. #{}{}",
                itemCount(),
                StructTree.objNum(parent),
                carved.isEmpty() ? "" : ", first carved out of #" + StructTree.objNum(carveFrom));
    }

    /**
     * Resolves the LBody of the nest target's last LI at apply time, or null when not nesting or
     * when the target no longer has an LI &gt; LBody to host the sublist.
     */
    private PdfStructElem sublistHost() {
        if (nestInto == null) {
            return null;
        }
        PdfStructElem lastLi = lastChildWithRole(nestInto, "LI");
        return lastLi == null ? null : lastChildWithRole(lastLi, "LBody");
    }

    private static PdfStructElem lastChildWithRole(PdfStructElem elem, String role) {
        List<PdfStructElem> children = StructTree.childrenOf(elem, PdfStructElem.class);
        for (int i = children.size() - 1; i >= 0; i--) {
            if (role.equals(StructTree.mappedRole(children.get(i)))) {
                return children.get(i);
            }
        }
        return null;
    }

    private int itemCount() {
        return wholeItems.size() + (carvedKids.isEmpty() ? 0 : 1);
    }

    /**
     * Removes the carved kids from their element's K array and returns them in reading order. They
     * are taken out by hand because there is no bulk MCR-move API; they go back in through {@link
     * #adoptCarvedKids}. Kids an earlier fix has already moved are skipped.
     */
    private List<PdfObject> detachCarvedKids() {
        if (carveFrom == null || carvedKids.isEmpty()) {
            return List.of();
        }
        PdfArray kArray = StructTree.kArrayAsArray(carveFrom);
        if (kArray == null) {
            return List.of();
        }

        List<Integer> indices = new ArrayList<>();
        for (PdfObject kid : carvedKids) {
            int index = indexOfInKArray(kArray, kid);
            if (index >= 0) {
                indices.add(index);
            } else {
                logger.debug(
                        "Carved kid no longer in K array of obj. #{}; skipping it",
                        StructTree.objNum(carveFrom));
            }
        }
        indices.sort(null);

        List<PdfObject> detached = new ArrayList<>();
        for (int index : indices) {
            detached.add(kArray.get(index, false));
        }
        for (int i = indices.size() - 1; i >= 0; i--) {
            kArray.remove(indices.get(i));
        }
        return detached;
    }

    /**
     * Re-attaches the carved kids as the list's first item.
     *
     * <p>Struct-element kids (dicts with /S) may be relocated by a raw K-array entry plus a /P
     * re-point: MCR registration keys off the leaf element that owns the marked content, not the
     * container, so reparenting a struct element does not disturb the ParentTree.
     *
     * <p>MCR kids (bare-int MCIDs or /Type /MCR dicts) are different: they ARE the marked content,
     * so they must go through addKid(), which re-registers them under the new P via the
     * ParentTreeHandler. A raw move leaves the page ParentTree pointing at the old parent,
     * producing the "inconsistent ParentTree mapping" that breaks page extraction and trips Acrobat
     * preflight.
     */
    private void adoptCarvedKids(
            DocContext ctx, ListAssembler assembler, PdfStructElem list, List<PdfObject> carved) {
        PdfStructElem lBody = assembler.newItemBody(list);
        PdfStructElem item =
                assembler.newContentIn(lBody, PdfName.P, ctx.doc().getPage(carvedPage));
        PdfArray itemK = new PdfArray();
        item.getPdfObject().put(PdfName.K, itemK);

        for (PdfObject kid : carved) {
            PdfObject resolved =
                    kid.isIndirectReference() ? ((PdfIndirectReference) kid).getRefersTo() : kid;
            if (resolved instanceof PdfDictionary dict && dict.containsKey(PdfName.S)) {
                itemK.add(kid);
                dict.put(PdfName.P, item.getPdfObject());
            } else if (resolved instanceof PdfNumber mcid) {
                item.addKid(new PdfMcrNumber(mcid, item));
            } else if (resolved instanceof PdfDictionary mcrDict) {
                item.addKid(new PdfMcrDictionary(mcrDict, item));
            } else {
                itemK.add(kid);
            }
        }
    }

    /** Drops the carved-from element when the carve took everything it held. */
    private void pruneIfEmptied(PdfStructElem parent) {
        if (carveFrom == null) {
            return;
        }
        PdfArray kArray = StructTree.kArrayAsArray(carveFrom);
        if (kArray != null && kArray.isEmpty()) {
            parent.removeKid(carveFrom);
        }
    }

    /** Finds a carved kid in a K array, matching indirect references against resolved objects. */
    private static int indexOfInKArray(PdfArray kArray, PdfObject carved) {
        for (int i = 0; i < kArray.size(); i++) {
            if (StructTree.isSame(kArray.get(i, false), carved)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfKid(PdfStructElem parent, PdfStructElem target) {
        List<IStructureNode> kids = parent.getKids();
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i) instanceof PdfStructElem kid && StructTree.isSameElement(kid, target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the element whose K array actually holds the target, walking up from its /P parent.
     * This handles cases where NeedlessNestingFix promoted children but iText's getKids() cache
     * became stale.
     */
    private PdfStructElem parentContaining(PdfStructElem target) {
        IStructureNode candidate = target.getParent();
        for (int depth = 0; depth < 10 && candidate instanceof PdfStructElem elem; depth++) {
            if (indexOfKid(elem, target) >= 0) {
                return elem;
            }
            candidate = elem.getParent();
        }
        return null;
    }

    private static PdfStructElem firstOrNull(List<PdfStructElem> elements) {
        return elements.isEmpty() ? null : elements.get(0);
    }

    @Override
    public int priority() {
        return 10; // after the split fix (5), which rebuilds lumped items in place
    }

    @Override
    public String describe() {
        return nestInto != null
                ? "Retagged " + itemCount() + " bulleted elements as a nested list"
                : "Retagged " + itemCount() + " bulleted elements as a list";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
