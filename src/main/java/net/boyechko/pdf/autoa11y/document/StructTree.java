/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for navigating and manipulating the PDF structure tree. */
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

    /** Returns the PDF object number for a structure element, or -1 if unavailable. */
    public static int objNum(PdfStructElem elem) {
        var ref = elem.getPdfObject().getIndirectReference();
        return ref != null ? ref.getObjNumber() : -1;
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

    /** Determines the page number for a structure element, using cache then recursion. */
    public static int determinePageNumber(DocContext ctx, PdfStructElem elem) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg != null) {
            int pageNum = ctx.doc().getPageNumber(pg);
            if (pageNum > 0) return pageNum;
        }

        // Check via indirect reference in the context cache
        int objNum = objNum(elem);
        if (objNum >= 0) {
            int pageNum = ctx.getPageNumber(objNum);
            if (pageNum > 0) return pageNum;
        }

        // Recursively check for the first child with a page
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    int pageNum = determinePageNumber(ctx, childElem);
                    if (pageNum > 0) return pageNum;
                }
            }
        }

        return 0;
    }

    /** Returns the page number for any MCR or OBJR (since PdfObjRef extends PdfMcr). */
    public static int pageOf(PdfMcr mcr) {
        PdfDictionary pageObj = mcr.getPageObject();
        if (pageObj == null) return 0;
        PdfDocument doc = pageObj.getIndirectReference().getDocument();
        return doc.getPageNumber(pageObj);
    }

    /**
     * Moves an element from one parent to another, updating the /K array and /P reference.
     *
     * @return true if the element was successfully moved
     */
    public static boolean moveElement(
            PdfStructElem fromParent, PdfStructElem elem, PdfStructElem toParent) {
        PdfDictionary fromDict = fromParent.getPdfObject();
        PdfObject kObj = fromDict.get(PdfName.K);
        if (kObj == null) return false;

        PdfObject elemObj = elem.getPdfObject();
        boolean removed = false;

        if (kObj instanceof PdfArray parentKids) {
            // Multiple children: find and remove from array
            for (int i = 0; i < parentKids.size(); i++) {
                PdfObject obj = parentKids.get(i);
                if (isSame(elemObj, obj)) {
                    parentKids.remove(i);
                    removed = true;
                    break;
                }
            }
        } else if (isSame(elemObj, kObj)) {
            // Single child stored as direct /K reference
            fromDict.remove(PdfName.K);
            removed = true;
        }

        if (removed) {
            elem.getPdfObject().put(PdfName.P, toParent.getPdfObject());
            toParent.addKid(elem);
            logger.debug("Moved {} into {}", Format.elem(elem), Format.elem(toParent));
        }

        return removed;
    }

    /** Finds the index of a kid element within a parent's kids list (via getKids). */
    public static int findKidIndex(IStructureNode parent, PdfStructElem target) {
        List<IStructureNode> kids = parent.getKids();
        if (kids == null) return -1;
        for (int i = 0; i < kids.size(); i++) {
            IStructureNode kid = kids.get(i);
            if (kid instanceof PdfStructElem elem && isSameElement(elem, target)) {
                return i;
            }
        }
        return -1;
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
            logger.debug("Pruning empty {} (obj. #{})", role, objNum(current));
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

    /** Gets the /K entry as an array from any structure node's dictionary. */
    public static PdfArray getKArray(IStructureNode node) {
        PdfDictionary dict = getPdfObject(node);
        return dict != null ? dict.getAsArray(PdfName.K) : null;
    }

    /**
     * Returns the /K entry as an array, converting a single-child /K object into a one-element
     * array in-place when needed.
     */
    public static PdfArray normalizeKArray(IStructureNode node) {
        PdfDictionary dict = getPdfObject(node);
        if (dict == null) {
            return null;
        }

        PdfObject kObj = dict.get(PdfName.K);
        if (kObj == null) {
            return null;
        }
        if (kObj instanceof PdfArray kArray) {
            return kArray;
        }

        PdfArray normalized = new PdfArray();
        normalized.add(kObj);
        dict.put(PdfName.K, normalized);
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
        PdfArray kArray = normalizeKArray(elem);
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

    /** Gets the underlying PdfDictionary for any structure node. */
    public static PdfDictionary getPdfObject(IStructureNode node) {
        if (node instanceof PdfStructElem elem) {
            return elem.getPdfObject();
        } else if (node instanceof PdfStructTreeRoot root) {
            return root.getPdfObject();
        }
        return null;
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

    /** Checks whether two structure elements refer to the same PDF object. */
    public static boolean isSameElement(PdfStructElem a, PdfStructElem b) {
        return a == b || isSame(a.getPdfObject(), b.getPdfObject());
    }

    /** Checks whether a candidate element is a descendant of an ancestor element. */
    public static boolean isDescendantOf(PdfStructElem candidate, PdfStructElem ancestor) {
        IStructureNode parent = candidate.getParent();
        while (parent instanceof PdfStructElem parentElem) {
            if (isSameElement(parentElem, ancestor)) {
                return true;
            }
            parent = parentElem.getParent();
        }
        return false;
    }

    /** Finds a structure element by its PDF object number, searching recursively. */
    public static PdfStructElem findByObjNumber(IStructureNode parent, int objNum) {
        List<IStructureNode> kids = parent.getKids();
        if (kids == null) return null;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                if (objNum(elem) == objNum) return elem;
                PdfStructElem found = findByObjNumber(elem, objNum);
                if (found != null) return found;
            }
        }
        return null;
    }

    // --- Children and descendants by type ---

    /**
     * Returns direct children of an element matching the given type. When {@code type} is {@code
     * PdfMcr.class}, OBJRs (which extend PdfMcr but have mcid &lt; 0) are excluded.
     */
    @SuppressWarnings("unchecked")
    public static <T extends IStructureNode> List<T> childrenOf(PdfStructElem elem, Class<T> type) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return List.of();

        List<T> out = new ArrayList<>();
        for (IStructureNode kid : kids) {
            if (type.isInstance(kid) && !isExcludedMcr(kid, type)) {
                out.add((T) kid);
            }
        }
        return out;
    }

    /**
     * Recursively collects all descendants matching the given type, traversing only through {@link
     * PdfStructElem} intermediaries. When {@code type} is {@code PdfMcr.class}, OBJRs are excluded.
     */
    public static <T extends IStructureNode> List<T> descendantsOf(
            PdfStructElem elem, Class<T> type) {
        List<T> result = new ArrayList<>();
        collectDescendants(elem, type, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IStructureNode> void collectDescendants(
            PdfStructElem elem, Class<T> type, List<T> result) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return;
        for (IStructureNode kid : kids) {
            if (type.isInstance(kid) && !isExcludedMcr(kid, type)) {
                result.add((T) kid);
            } else if (kid instanceof PdfStructElem childElem) {
                collectDescendants(childElem, type, result);
            }
        }
    }

    /** When collecting PdfMcr, exclude OBJRs (which have getMcid() &lt; 0). */
    private static boolean isExcludedMcr(IStructureNode kid, Class<?> type) {
        return type == PdfMcr.class && kid instanceof PdfMcr mcr && mcr.getMcid() < 0;
    }

    /**
     * Returns the mapped role for a structure element, or the raw role if no mapping is available.
     */
    public static String mappedRole(PdfStructElem n) {
        PdfName role = n.getRole();
        PdfStructTreeRoot structTreeRoot = pdfDocumentFor(n).getStructTreeRoot();
        if (structTreeRoot == null) {
            return role.getValue();
        }
        PdfDictionary roleMap = structTreeRoot.getRoleMap();

        if (roleMap != null) {
            PdfName mappedRole = roleMap.getAsName(role);
            return (mappedRole != null) ? mappedRole.getValue() : role.getValue();
        }
        return role.getValue();
    }

    /** Returns the parent of a structure element, or null if no parent is available. */
    public static PdfStructElem parentOf(PdfStructElem n) {
        IStructureNode p = n.getParent();
        return (p instanceof PdfStructElem) ? (PdfStructElem) p : null;
    }

    // === Miscellaneous utilities ===========================================

    public static final String SCRIBBLE_PREFIX = "__";
    public static final String SCRIBBLE_SEPARATOR = "; ";

    /** Returns the scribble value (prefix-stripped, control-char-cleaned), or null if absent. */
    public static DocValue.Scribble getScribble(PdfStructElem elem) {
        return DocValue.Scribble.of(elem);
    }

    public static void setScribble(PdfStructElem elem, String scribble) {
        elem.put(PdfName.T, new PdfString(SCRIBBLE_PREFIX + scribble));
    }

    /** Appends to an existing scribble or creates a new one if none exists. */
    public static void addScribble(PdfStructElem elem, String scribble) {
        DocValue.Scribble existing = getScribble(elem);
        if (existing == null) {
            setScribble(elem, scribble);
        } else {
            setScribble(elem, existing.value() + SCRIBBLE_SEPARATOR + scribble);
        }
    }

    /**
     * Removes scribble segments whose first whitespace-separated token equals {@code tag}. If the
     * element has no scribble, this is a no-op. If all segments are removed, /T is cleared.
     *
     * <p>Intended for checks that rewrite their own diagnostics on each run: clear the check's own
     * segments before re-emitting, so stale violations don't accumulate.
     */
    public static boolean clearScribbleSegments(PdfStructElem elem, String tag) {
        DocValue.Scribble existing = getScribble(elem);
        if (existing == null) return false;

        String[] segments =
                existing.value().split(java.util.regex.Pattern.quote(SCRIBBLE_SEPARATOR));
        StringBuilder kept = new StringBuilder();
        boolean removedAny = false;
        for (String seg : segments) {
            int sp = seg.indexOf(' ');
            String head = (sp < 0) ? seg : seg.substring(0, sp);
            if (head.equals(tag)) {
                removedAny = true;
                continue;
            }
            if (kept.length() > 0) kept.append(SCRIBBLE_SEPARATOR);
            kept.append(seg);
        }
        if (!removedAny) return false;
        if (kept.length() == 0) {
            elem.getPdfObject().remove(PdfName.T);
        } else {
            setScribble(elem, kept.toString());
        }
        return true;
    }

    /**
     * Walks the structure tree below {@code root}, clearing scribble segments tagged {@code tag} on
     * every element. Returns {@code true} if any element was modified.
     *
     * <p>Prefer this single pre-pass over per-node clearing in {@code enterElement}: the latter
     * would wipe scribbles that a parent's validator wrote to a child element before the child is
     * visited.
     */
    public static boolean clearScribbleSegmentsInTree(PdfStructTreeRoot root, String tag) {
        if (root == null || root.getKids() == null) return false;
        boolean dirty = false;
        for (IStructureNode kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                dirty |= clearScribbleSegmentsInSubtree(elem, tag);
            }
        }
        return dirty;
    }

    private static boolean clearScribbleSegmentsInSubtree(PdfStructElem elem, String tag) {
        boolean dirty = clearScribbleSegments(elem, tag);
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem child) {
                    dirty |= clearScribbleSegmentsInSubtree(child, tag);
                }
            }
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
}
