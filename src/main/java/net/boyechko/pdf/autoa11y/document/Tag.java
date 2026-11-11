// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;

/**
 * A structure element as the rest of the tool speaks of it: an adapter over iText's {@link
 * PdfStructElem}. It holds nothing but the element and caches nothing, because iText re-wraps
 * elements on every read and invalidates wrappers on some mutations. Two tags are equal when they
 * stand for the same PDF object, whichever wrappers they were made from.
 */
public final class Tag {
    private final PdfStructElem elem;

    private Tag(PdfStructElem elem) {
        this.elem = elem;
    }

    public static Tag of(PdfStructElem elem) {
        return new Tag(elem);
    }

    /** The underlying iText element, for operations Tag does not yet offer. */
    public PdfStructElem elem() {
        return elem;
    }

    /** The PDF object number, or -1 if the element is not an indirect object. */
    public int objNum() {
        var ref = elem.getPdfObject().getIndirectReference();
        return ref != null ? ref.getObjNumber() : -1;
    }

    /** The role after the document's role map, or the raw role if it maps to nothing. */
    public String role() {
        PdfName role = elem.getRole();
        PdfStructTreeRoot root = document().getStructTreeRoot();
        PdfDictionary roleMap = root != null ? root.getRoleMap() : null;
        PdfName mapped = roleMap != null ? roleMap.getAsName(role) : null;
        return (mapped != null ? mapped : role).getValue();
    }

    /**
     * The element's page via its own /Pg, else the first page among its kids in reading order: an
     * MCR or OBJR naming its own page, or an element kid resolved the same way. Zero if none.
     */
    public int page() {
        // A page dictionary is always indirect, so it reaches the document even
        // when the element itself is a direct object.
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg != null && pg.getIndirectReference() != null) {
            int pageNum = pg.getIndirectReference().getDocument().getPageNumber(pg);
            if (pageNum > 0) return pageNum;
        }
        for (IStructureNode kid : StructTree.kidsOf(elem)) {
            int pageNum =
                    switch (kid) {
                        case PdfMcr mcr -> StructTree.pageOf(mcr);
                        case PdfStructElem child -> Tag.of(child).page();
                        default -> 0;
                    };
            if (pageNum > 0) return pageNum;
        }
        return 0;
    }

    /** The enclosing element, or null when the element sits directly under the tree root. */
    public Tag parent() {
        return elem.getParent() instanceof PdfStructElem parent ? Tag.of(parent) : null;
    }

    /** This tag or its nearest ancestor with one of the mapped roles, or null. */
    public Tag closest(String... roles) {
        List<String> wanted = List.of(roles);
        Tag current = this;
        while (current != null && !wanted.contains(current.role())) {
            current = current.parent();
        }
        return current;
    }

    /** Whether the ancestor encloses this tag; a tag is not its own descendant. */
    public boolean isDescendantOf(Tag ancestor) {
        for (Tag up = parent(); up != null; up = up.parent()) {
            if (up.equals(ancestor)) return true;
        }
        return false;
    }

    /** The deepest tag that is, or encloses, every one of the given tags, or null. */
    public static Tag commonAncestorOf(List<Tag> tags) {
        for (Tag candidate = tags.get(0); candidate != null; candidate = candidate.parent()) {
            Tag ancestor = candidate;
            if (tags.stream().allMatch(t -> t.equals(ancestor) || t.isDescendantOf(ancestor))) {
                return candidate;
            }
        }
        return null;
    }

    public List<Tag> children() {
        return kids(PdfStructElem.class).stream().map(Tag::of).toList();
    }

    /** Marked-content kids, excluding OBJRs (which extend PdfMcr but mark no content). */
    public List<PdfMcr> mcrs() {
        return kids(PdfMcr.class);
    }

    public List<PdfObjRef> objRefs() {
        return kids(PdfObjRef.class);
    }

    /**
     * Whether the tag has kids and every one is marked content. An element or an OBJR among them
     * disqualifies it, since an OBJR marks no content.
     */
    public boolean holdsOnlyMarkedContent() {
        int kids = StructTree.kidsOf(elem).size();
        return kids > 0 && mcrs().size() == kids;
    }

    /** Marked-content references anywhere below, excluding OBJRs. */
    public List<PdfMcr> descendantMcrs() {
        return descendants(PdfMcr.class);
    }

    public List<PdfObjRef> descendantObjRefs() {
        return descendants(PdfObjRef.class);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tag that
                && (elem == that.elem
                        || StructTree.isSame(elem.getPdfObject(), that.elem.getPdfObject()));
    }

    @Override
    public int hashCode() {
        int objNum = objNum();
        return objNum >= 0 ? objNum : System.identityHashCode(elem.getPdfObject());
    }

    @Override
    public String toString() {
        return Format.elem(elem);
    }

    /** Direct kids of the given type. */
    @SuppressWarnings("unchecked")
    <T extends IStructureNode> List<T> kids(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (IStructureNode kid : StructTree.kidsOf(elem)) {
            if (type.isInstance(kid) && !isObjRefAmongMcrs(kid, type)) {
                out.add((T) kid);
            }
        }
        return out;
    }

    /** Kids of the given type at any depth, traversing only through element intermediaries. */
    <T extends IStructureNode> List<T> descendants(Class<T> type) {
        List<T> out = new ArrayList<>();
        collectDescendants(elem, type, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IStructureNode> void collectDescendants(
            PdfStructElem elem, Class<T> type, List<T> out) {
        for (IStructureNode kid : StructTree.kidsOf(elem)) {
            if (type.isInstance(kid) && !isObjRefAmongMcrs(kid, type)) {
                out.add((T) kid);
            } else if (kid instanceof PdfStructElem child) {
                collectDescendants(child, type, out);
            }
        }
    }

    /** True for an OBJR when collecting PdfMcr, since OBJRs have no MCID (getMcid() &lt; 0). */
    private static boolean isObjRefAmongMcrs(IStructureNode kid, Class<?> type) {
        return type == PdfMcr.class && kid instanceof PdfMcr mcr && mcr.getMcid() < 0;
    }

    private PdfDocument document() {
        return elem.getPdfObject().getIndirectReference().getDocument();
    }
}
