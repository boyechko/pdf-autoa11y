// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;

/**
 * Operations on the PDF structure tree as a whole, plus the low-level /K-array and object-identity
 * work that has no better owner yet. What one element holds, and moving nodes among elements,
 * belongs to {@link Tag} and the other {@link StructNode} kinds; an operation that would read
 * better as a message to one of them moves there and is never duplicated here.
 */
public final class StructTree {

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
