// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagutils.TagStructureContext;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Low-level operations on the PDF structure tree: mutation, marked-content bookkeeping, attributes,
 * and scribbles. Reading an element (its role, relatives, kids, or page) belongs to {@link Tag}.
 * When an operation here would read better as a message to a Tag, it moves there; it is never
 * duplicated in both.
 */
public final class StructTree {
    private static final Logger logger = LoggerFactory.getLogger(StructTree.class);

    private StructTree() {}

    /** Returns the PDF document for the given structure element. */
    public static PdfDocument pdfDocumentFor(PdfStructElem n) {
        return n.getPdfObject().getIndirectReference().getDocument();
    }

    /** Returns the PDF document for the given structure element. */
    public static PdfDocument pdfDocumentFor(IStructureNode n) {
        IStructureNode current = n;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        PdfStructTreeRoot root = (PdfStructTreeRoot) current;
        return root.getDocument();
    }

    /** Finds the first child element with the given role. */
    public static PdfStructElem findFirstChild(IStructureNode parent, PdfName role) {
        List<IStructureNode> kids = parent.getKids();
        if (kids == null) return null;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                if (elem.getRole() == role) {
                    return elem;
                }
            }
        }
        return null;
    }

    /** Finds the Document element in the structure tree. */
    public static PdfStructElem findDocument(IStructureNode root) {
        return findFirstChild(root, PdfName.Document);
    }

    /** Checks whether a /Pg dictionary refers to the same page. */
    public static boolean isSamePage(PdfDictionary pgDict, PdfPage targetPage) {
        PdfDictionary targetDict = targetPage.getPdfObject();
        if (pgDict.equals(targetDict)) {
            return true;
        }
        if (pgDict.getIndirectReference() != null && targetDict.getIndirectReference() != null) {
            return pgDict.getIndirectReference().equals(targetDict.getIndirectReference());
        }
        return false;
    }

    /** Returns the page number for any MCR or OBJR (since PdfObjRef extends PdfMcr). */
    public static int pageOf(PdfMcr mcr) {
        // getPageObject() would NPE inside iText when encountering orphaned MCRs.
        if (mcr.getPageIndirectReference() == null) return 0;
        PdfDictionary pageObj = mcr.getPageObject();
        if (pageObj == null) return 0;
        PdfDocument doc = pageObj.getIndirectReference().getDocument();
        return doc.getPageNumber(pageObj);
    }

    /** Returns the page an MCR renders on, refusing when it cannot be resolved. */
    public static PdfPage requirePageOf(PdfMcr mcr) {
        int pageNum = pageOf(mcr);
        if (pageNum <= 0) {
            throw new IllegalStateException("Cannot resolve the page of MCID " + mcr.getMcid());
        }
        return mcr.getPageIndirectReference().getDocument().getPage(pageNum);
    }

    /**
     * Adds a fresh-MCID marked-content reference on the page to the parent and returns it: a bare
     * number when the parent is pinned to that page, else an MCR dictionary carrying its own /Pg.
     */
    public static PdfMcr addNewMcr(PdfStructElem parent, PdfPage page) {
        PdfDictionary pg = parent.getPdfObject().getAsDictionary(PdfName.Pg);
        PdfMcr mcr =
                pg != null && isSamePage(pg, page)
                        ? new PdfMcrNumber(page, parent)
                        : new PdfMcrDictionary(page, parent);
        return parent.addKid(mcr);
    }

    /** Walks up ancestors from {@code elem} and returns the first /Pg page reference found. */
    public static PdfObject effectivePageDict(PdfStructElem elem) {
        IStructureNode current = elem;
        while (current instanceof PdfStructElem se) {
            PdfObject pg = se.getPdfObject().get(PdfName.Pg);
            if (pg != null) {
                return pg;
            }
            current = se.getParent();
        }
        return null;
    }

    /**
     * Moves a kid (struct elem, MCR, or OBJR) from one parent to another, keeping it on the page it
     * renders on. Delegates to iText's {@link TagTreePointer#relocateKid}, which rebinds the kid to
     * its new parent and, for an MCR or OBJR, upgrades a bare MCID number to a dictionary carrying
     * its own /Pg whenever the destination sits on a different page.
     *
     * @throws IllegalArgumentException if {@code kid} is not currently a kid of {@code fromParent}
     */
    static void moveKid(IStructureNode kid, PdfStructElem fromParent, PdfStructElem toParent) {
        moveKid(kid, fromParent, toParent, -1);
    }

    /** As {@link #moveKid}, landing at {@code toIndex} among the new parent's kids. */
    static void moveKid(
            IStructureNode kid, PdfStructElem fromParent, PdfStructElem toParent, int toIndex) {
        int index = findKidIndex(fromParent, kid);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Cannot move " + Format.node(kid) + " out of " + Format.elem(fromParent));
        }
        // iText resolves an MCR's page from its immediate parent only and
        // dereferences the result unguarded, while a tagger may leave /Pg on a
        // more distant ancestor. Writing the inherited value down is a no-op
        // for readers.
        materializeInheritedPage(fromParent);
        materializeInheritedPage(toParent);
        TagStructureContext context = pdfDocumentFor(fromParent).getTagStructureContext();
        TagTreePointer destination = context.createPointerForStructElem(toParent);
        if (toIndex >= 0) {
            destination.setNextNewKidIndex(toIndex);
        }
        context.createPointerForStructElem(fromParent).relocateKid(index, destination);
    }

    /**
     * Finds the index of a kid within a parent's kids list (via getKids). Matching is by underlying
     * PDF object, so {@code target} must be a node obtained from the tree rather than a freshly
     * constructed wrapper: a bare MCID number is a direct object, for which identity is the only
     * available test.
     */
    public static int findKidIndex(IStructureNode parent, IStructureNode target) {
        PdfObject targetObject = pdfObjectOf(target);
        if (targetObject == null) {
            logger.warn("Unexpected kid type {} in findKidIndex", target.getClass());
            return -1;
        }
        List<IStructureNode> kids = kidsOf(parent);
        for (int i = 0; i < kids.size(); i++) {
            if (isSame(pdfObjectOf(kids.get(i)), targetObject)) {
                return i;
            }
        }
        return -1;
    }

    /** True if the elements are consecutive kids of {@code parent}, in the given order. */
    public static boolean areConsecutiveKids(PdfStructElem parent, List<PdfStructElem> elems) {
        if (elems.isEmpty()) {
            return false;
        }
        int first = findKidIndex(parent, elems.get(0));
        List<IStructureNode> kids = kidsOf(parent);
        if (first < 0 || first + elems.size() > kids.size()) {
            return false;
        }
        for (int i = 1; i < elems.size(); i++) {
            if (!(kids.get(first + i) instanceof PdfStructElem sibling)
                    || !Tag.of(sibling).equals(Tag.of(elems.get(i)))) {
                return false;
            }
        }
        return true;
    }

    /** Returns the underlying PDF object of a structure node, or null if it is neither kind. */
    private static PdfObject pdfObjectOf(IStructureNode node) {
        if (node instanceof PdfStructElem elem) return elem.getPdfObject();
        if (node instanceof PdfMcr mcr) return mcr.getPdfObject();
        return null;
    }

    /** Copies an ancestor's /Pg onto the element itself, so iText can resolve its kids' pages. */
    public static void materializeInheritedPage(PdfStructElem elem) {
        if (elem.getPdfObject().get(PdfName.Pg) != null) return;
        PdfObject pg = effectivePageDict(elem);
        if (pg == null) return;
        PdfIndirectReference ref = pg.getIndirectReference();
        elem.put(PdfName.Pg, ref != null ? ref : pg);
    }

    /** Returns a parent's kids, or an empty list where getKids reports none. */
    static List<IStructureNode> kidsOf(IStructureNode parent) {
        List<IStructureNode> kids = parent.getKids();
        return kids == null ? List.of() : kids;
    }

    /** Adds a kid at a specific index (works with both PdfStructElem and PdfStructTreeRoot). */
    public static void addKidToParent(IStructureNode parent, int index, PdfStructElem kid) {
        if (parent instanceof PdfStructElem parentElem) {
            parentElem.addKid(index, kid);
        } else if (parent instanceof PdfStructTreeRoot root) {
            root.addKid(index, kid);
        }
    }

    /** Removes an element from its parent (works with both PdfStructElem and PdfStructTreeRoot). */
    public static void removeFromParent(PdfStructElem elem, IStructureNode parent) {
        if (parent instanceof PdfStructElem parentElem) {
            parentElem.removeKid(elem);
        } else if (parent instanceof PdfStructTreeRoot root) {
            PdfObject kObj = root.getPdfObject().get(PdfName.K);
            if (kObj instanceof PdfArray kArray) {
                kArray.remove(elem.getPdfObject());
                if (elem.getPdfObject().getIndirectReference() != null) {
                    kArray.remove(elem.getPdfObject().getIndirectReference());
                }
            }
        }
    }

    /**
     * Removes the element if it has no kids, then cascades upward: if the parent becomes empty, it
     * is removed too. Stops at the Document element (direct child of StructTreeRoot). Returns the
     * number of elements removed.
     */
    public static int pruneEmpty(PdfStructElem elem) {
        int removed = 0;
        PdfStructElem current = elem;
        while (current != null) {
            List<IStructureNode> kids = current.getKids();
            if (kids != null && !kids.isEmpty()) {
                break;
            }
            IStructureNode parent = current.getParent();
            if (parent == null || parent instanceof PdfStructTreeRoot) {
                break;
            }
            String role = current.getRole() != null ? current.getRole().getValue() : "unknown";
            logger.debug("Pruning empty {} (obj. #{})", role, Tag.of(current).objNum());
            removeFromParent(current, parent);
            removed++;
            current = (parent instanceof PdfStructElem parentElem) ? parentElem : null;
        }
        return removed;
    }

    /* The PDF spec allows /K to be either a single object or an array. iText
     * follows this: when a structure element has one child, it stores /K as a
     * direct dictionary reference. Only with 2+ children does it upgrade to a
     * PdfArray. This means getAsArray(PdfName.K) returns null for single-child
     * elements. */

    /**
     * Returns the /K entry as an array, converting a single-child /K object into a one-element
     * array in-place when needed.
     */
    public static PdfArray kArrayAsArray(PdfStructElem node) {
        PdfObject kObj = node.getPdfObject().get(PdfName.K);
        if (kObj == null) {
            return null;
        }
        if (kObj instanceof PdfArray kArray) {
            return kArray;
        }

        PdfArray normalized = new PdfArray();
        normalized.add(kObj);
        node.getPdfObject().put(PdfName.K, normalized);
        return normalized;
    }

    /** Finds the index of an element in a /K array. Returns -1 if not found. */
    public static int findIndexInKArray(PdfArray kArray, PdfStructElem elem) {
        PdfObject elemObj = elem.getPdfObject();
        for (int i = 0; i < kArray.size(); i++) {
            PdfObject obj = kArray.get(i);
            if (isSame(obj, elemObj)) {
                return i;
            }
        }
        return -1;
    }

    /** Removes an MCR entry with the given MCID from an element's K array. */
    public static boolean removeMcr(PdfStructElem elem, int mcid) {
        PdfArray kArray = kArrayAsArray(elem);
        if (kArray == null) {
            return false;
        }
        for (int i = kArray.size() - 1; i >= 0; i--) {
            PdfObject obj = kArray.get(i);
            if (obj instanceof PdfNumber num && num.intValue() == mcid) {
                kArray.remove(i);
                return true;
            }
            if (obj instanceof PdfDictionary dict) {
                PdfNumber mcidNum = dict.getAsNumber(PdfName.MCID);
                if (mcidNum != null && mcidNum.intValue() == mcid) {
                    kArray.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether two PdfObjects refer to the same underlying PDF object. Handles any
     * combination of resolved dictionaries and indirect references, in either argument order.
     */
    public static boolean isSame(PdfObject a, PdfObject b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // a might be an indirect ref pointing to b, or vice versa
        PdfIndirectReference aRef = a.getIndirectReference();
        PdfIndirectReference bRef = b.getIndirectReference();
        if (aRef != null && aRef.equals(b)) return true;
        if (bRef != null && bRef.equals(a)) return true;
        return aRef != null && aRef.equals(bRef);
    }

    /** Finds a structure element by its PDF object number, searching recursively. */
    public static PdfStructElem findByObjNumber(IStructureNode parent, int objNum) {
        List<IStructureNode> kids = parent.getKids();
        if (kids == null) return null;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                if (Tag.of(elem).objNum() == objNum) return elem;
                PdfStructElem found = findByObjNumber(elem, objNum);
                if (found != null) return found;
            }
        }
        return null;
    }

    // === Structure attributes ==============================================

    /**
     * Reads one attribute value from an element's {@code /A}, taking the first attribute dictionary
     * whose owner ({@code /O}) matches. {@code /A} may hold a single dictionary or an array;
     * revision numbers interleaved in the array are skipped. Class-map attributes ({@code /C}) are
     * not consulted.
     */
    public static PdfObject attributeValue(PdfStructElem elem, PdfName owner, PdfName key) {
        PdfObject attrs = elem.getPdfObject().get(PdfName.A);
        if (attrs instanceof PdfDictionary dict) {
            return ownedValue(dict, owner, key);
        }
        if (attrs instanceof PdfArray array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof PdfDictionary dict) {
                    PdfObject value = ownedValue(dict, owner, key);
                    if (value != null) return value;
                }
            }
        }
        return null;
    }

    /**
     * Writes one attribute value into the element's first {@code /A} dictionary owned by {@code
     * owner}, adding such a dictionary (and promoting {@code /A} to an array) when none exists.
     */
    public static void setAttributeValue(
            PdfStructElem elem, PdfName owner, PdfName key, PdfObject value) {
        PdfObject attrs = elem.getPdfObject().get(PdfName.A);
        PdfDictionary owned = ownedDictionary(attrs, owner);
        if (owned != null) {
            owned.put(key, value);
            return;
        }
        PdfDictionary added = new PdfDictionary();
        added.put(PdfName.O, owner);
        added.put(key, value);
        if (attrs instanceof PdfArray array) {
            array.add(added);
        } else if (attrs instanceof PdfDictionary dict) {
            elem.getPdfObject().put(PdfName.A, new PdfArray(List.of(dict, added)));
        } else {
            elem.getPdfObject().put(PdfName.A, added);
        }
    }

    private static PdfDictionary ownedDictionary(PdfObject attrs, PdfName owner) {
        if (attrs instanceof PdfDictionary dict) {
            return owner.equals(dict.getAsName(PdfName.O)) ? dict : null;
        }
        if (attrs instanceof PdfArray array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof PdfDictionary dict
                        && owner.equals(dict.getAsName(PdfName.O))) {
                    return dict;
                }
            }
        }
        return null;
    }

    private static PdfObject ownedValue(PdfDictionary attrs, PdfName owner, PdfName key) {
        return owner.equals(attrs.getAsName(PdfName.O)) ? attrs.get(key) : null;
    }

    // === Miscellaneous utilities ===========================================

    /**
     * Walks the structure tree below {@code root}, clearing scribble segments headed {@code head}
     * on every element. Returns {@code true} if any element was modified.
     *
     * <p>Prefer this single pre-pass over per-node clearing in {@code enterElement}: the latter
     * would wipe scribbles that a parent's validator wrote to a child element before the child is
     * visited.
     */
    public static boolean clearScribbleSegmentsInTree(PdfStructTreeRoot root, String head) {
        if (root == null || root.getKids() == null) return false;
        boolean dirty = false;
        for (IStructureNode kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                dirty |= clearScribbleSegmentsInSubtree(Tag.of(elem), head);
            }
        }
        return dirty;
    }

    private static boolean clearScribbleSegmentsInSubtree(Tag tag, String head) {
        DocValue.Scribble before = tag.scribble();
        DocValue.Scribble after = before.without(head);
        boolean dirty = !after.equals(before);
        if (dirty) tag.scribble(after);
        for (Tag child : tag.children()) {
            dirty |= clearScribbleSegmentsInSubtree(child, head);
        }
        return dirty;
    }

    /**
     * Navigates to a descendant by a sequence of child indices.
     *
     * <p>For example, {@code getDescendant(root, 0, 2, 0)} is equivalent to {@code
     * root.getKids().get(0).getKids().get(2).getKids().get(0)}.
     */
    public static IStructureNode getDescendant(IStructureNode root, int... indices) {
        IStructureNode current = root;
        for (int idx : indices) {
            current = current.getKids().get(idx);
        }
        return current;
    }

    // === Role tree representation ==========================================

    /** Converts a PdfStructElem tree into a TreeNode tree of role-name strings. */
    public static TreeNode<String> toRoleTree(PdfStructElem elem) {
        List<TreeNode<String>> childNodes = new ArrayList<>();
        for (Tag kid : Tag.of(elem).children()) {
            childNodes.add(toRoleTree(kid.elem()));
        }
        return new TreeNode<>(elem.getRole().getValue(), childNodes);
    }

    /** Converts a PdfStructElem tree into a compact bracket-notation string. */
    public static String toRoleTreeString(PdfStructElem elem) {
        return RoleTreeNotation.format(toRoleTree(elem)).replaceAll("\\s+", "");
    }
}
