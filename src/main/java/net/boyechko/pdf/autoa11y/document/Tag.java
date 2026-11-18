// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagutils.TagStructureContext;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A structure element as the rest of the tool speaks of it: an adapter over iText's {@link
 * PdfStructElem}. It holds nothing but the element and caches nothing, because iText re-wraps
 * elements on every read and invalidates wrappers on some mutations. Two tags are equal when they
 * stand for the same PDF object, whichever wrappers they were made from.
 */
public final class Tag implements StructNode {
    private static final Logger logger = LoggerFactory.getLogger(Tag.class);
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
     * The element's /T text with the control characters Acrobat appends stripped, or null when the
     * key is absent or holds nothing else. Whether that text is a title or a scribble is for {@link
     * DocValue.Title} and {@link #scribble()} to say.
     */
    String titleText() {
        PdfString t = elem.getPdfObject().getAsString(PdfName.T);
        if (t == null) return null;
        String value = t.toUnicodeString().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return value.isEmpty() ? null : value;
    }

    /** The element's scribble; {@link DocValue.Scribble#EMPTY} when it has none. */
    public DocValue.Scribble scribble() {
        return DocValue.Scribble.fromRaw(titleText());
    }

    /** Writes the scribble to /T, removing the key for an empty one. */
    public void scribble(DocValue.Scribble scribble) {
        if (scribble.isEmpty()) {
            elem.getPdfObject().remove(PdfName.T);
        } else {
            elem.put(PdfName.T, new PdfString(scribble.rawValue()));
        }
    }

    /** Appends a tool segment to the scribble; see {@link DocValue.Scribble#with}. */
    public void stamp(String segment) {
        scribble(scribble().with(segment));
    }

    /**
     * Leads the scribble with the element's object number; see {@link
     * DocValue.Scribble#withObjNum}.
     */
    public void stampObjNum() {
        scribble(scribble().withObjNum(objNum()));
    }

    /** Drops the scribble segments headed {@code head}; see {@link DocValue.Scribble#without}. */
    public void unstamp(String head) {
        scribble(scribble().without(head));
    }

    /**
     * The element's page via its own /Pg, else the first page among its kids in reading order: an
     * MCR or OBJR naming its own page, or an element kid resolved the same way. Zero if none.
     */
    @Override
    public int page() {
        // A page dictionary is always indirect, so it reaches the document even
        // when the element itself is a direct object.
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg != null && pg.getIndirectReference() != null) {
            int pageNum = pg.getIndirectReference().getDocument().getPageNumber(pg);
            if (pageNum > 0) return pageNum;
        }
        for (StructNode kid : kids()) {
            int pageNum = kid.page();
            if (pageNum > 0) return pageNum;
        }
        return 0;
    }

    /**
     * The /Pg entry the tag resolves its page through: its own, else its nearest ancestor's; null
     * when none of them names a page.
     */
    public PdfObject pageEntry() {
        for (Tag tag = this; tag != null; tag = tag.parent()) {
            PdfObject pg = tag.elem.getPdfObject().get(PdfName.Pg);
            if (pg != null) return pg;
        }
        return null;
    }

    /** Copies an ancestor's /Pg onto the tag itself, so iText can resolve its kids' pages. */
    public void materializeInheritedPage() {
        if (elem.getPdfObject().get(PdfName.Pg) != null) return;
        PdfObject pg = pageEntry();
        if (pg == null) return;
        PdfIndirectReference ref = pg.getIndirectReference();
        elem.put(PdfName.Pg, ref != null ? ref : pg);
    }

    /**
     * Reads one attribute value from the element's {@code /A}, taking the first attribute
     * dictionary whose owner ({@code /O}) matches. {@code /A} may hold a single dictionary or an
     * array; revision numbers interleaved in the array are skipped. Class-map attributes ({@code
     * /C}) are not consulted.
     */
    public PdfObject attribute(PdfName owner, PdfName key) {
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
    public void attribute(PdfName owner, PdfName key, PdfObject value) {
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

    /** The enclosing element, or null when the element sits directly under the tree root. */
    @Override
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

    /** Everything the tag holds, of every kind, in document order. */
    public List<StructNode> kids() {
        return kidsOf(elem).stream().map(StructNode::of).toList();
    }

    public List<Tag> children() {
        return kids(PdfStructElem.class).stream().map(Tag::of).toList();
    }

    /** Marked-content kids, excluding OBJRs (which extend PdfMcr but mark no content). */
    public List<MarkedContent> markedContent() {
        return kids(PdfMcr.class).stream().map(MarkedContent::of).toList();
    }

    public List<ObjectRef> objectRefs() {
        return kids(PdfObjRef.class).stream().map(ObjectRef::of).toList();
    }

    /**
     * Whether the tag has kids and every one is marked content. An element or an OBJR among them
     * disqualifies it, since an OBJR marks no content.
     */
    public boolean holdsOnlyMarkedContent() {
        List<StructNode> kids = kids();
        return !kids.isEmpty() && kids.stream().allMatch(kid -> kid instanceof MarkedContent);
    }

    /** Marked-content references anywhere below, excluding OBJRs. */
    public List<MarkedContent> descendantMarkedContent() {
        return descendants(PdfMcr.class).stream().map(MarkedContent::of).toList();
    }

    public List<ObjectRef> descendantObjectRefs() {
        return descendants(PdfObjRef.class).stream().map(ObjectRef::of).toList();
    }

    /**
     * Adds marked content with a fresh MCID on the page as the tag's last kid: a bare number when
     * the tag is pinned to that page, else a reference carrying its own page.
     */
    public MarkedContent addMarkedContent(PdfPage page) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        PdfMcr mcr =
                pg != null && StructTree.isSamePage(pg, page)
                        ? new PdfMcrNumber(page, elem)
                        : new PdfMcrDictionary(page, elem);
        return MarkedContent.of(elem.addKid(mcr));
    }

    /** Whether the parent this tag names lists it among its kids; false for a removed tag. */
    public boolean isAttached() {
        return indexInParent() >= 0;
    }

    /** The kid right before this one in its parent, or null if none or not an element. */
    public Tag previousSibling() {
        List<StructNode> preceding = precedingSiblings();
        return !preceding.isEmpty() && preceding.getLast() instanceof Tag previous
                ? previous
                : null;
    }

    /** The kid right after this one in its parent, or null if none or not an element. */
    public Tag nextSibling() {
        List<StructNode> following = followingSiblings();
        return !following.isEmpty() && following.getFirst() instanceof Tag next ? next : null;
    }

    /**
     * Whether the tags sit side by side in one parent, in the given order, with nothing between.
     */
    public static boolean areConsecutiveSiblings(List<Tag> tags) {
        if (tags.isEmpty() || !tags.get(0).isAttached()) {
            return false;
        }
        List<StructNode> following = tags.get(0).followingSiblings();
        List<Tag> rest = tags.subList(1, tags.size());
        return following.size() >= rest.size() && following.subList(0, rest.size()).equals(rest);
    }

    /** Its parent's kids of every kind that come before this tag, in order. */
    public List<StructNode> precedingSiblings() {
        int index = indexInParent();
        return index < 0 ? List.of() : siblings().subList(0, index);
    }

    /** Its parent's kids of every kind that come after this tag, in order. */
    public List<StructNode> followingSiblings() {
        int index = indexInParent();
        List<StructNode> siblings = siblings();
        return index < 0 ? List.of() : siblings.subList(index + 1, siblings.size());
    }

    /** Places this tag right before the sibling, taking it out of wherever it sits now. */
    @Override
    public void insertBefore(Tag sibling) {
        insertBeside(sibling, 0);
    }

    /** Places this tag right after the sibling, taking it out of wherever it sits now. */
    public void insertAfter(Tag sibling) {
        insertBeside(sibling, 1);
    }

    /**
     * Moves marked content or an object reference out of its parent to sit right before this tag.
     */
    void precedeWithContent(PdfMcr content) {
        requireAttached();
        Tag container = parent();
        if (container == null) {
            throw new IllegalStateException("Marked content cannot sit beside top-level " + this);
        }
        container.adopt(content, indexInParent());
    }

    @Override
    public Tag moveTo(Tag newParent) {
        if (parent() == null) {
            throw new IllegalStateException("Cannot move top-level " + this);
        }
        newParent.adopt(elem, -1);
        return this;
    }

    /** Rearranges the tag's kids into the given order, which must name each of them once. */
    public void reorderKids(List<StructNode> order) {
        List<StructNode> current = kids();
        if (order.size() != current.size() || !order.containsAll(current)) {
            throw new IllegalArgumentException("Not a reordering of the kids of " + this);
        }
        // Detach all, then append in the new order; iText keeps the parent tree in step.
        for (StructNode kid : order) {
            elem.removeKid(iTextNodeOf(kid));
        }
        for (StructNode kid : order) {
            switch (kid) {
                case Tag tag -> elem.addKid(tag.elem);
                case MarkedContent content -> elem.addKid(content.mcr());
                case ObjectRef ref -> elem.addKid(ref.objRef());
            }
        }
    }

    /** Moves everything this tag holds to the end of another tag's kids, in order. */
    public void moveKidsTo(Tag newParent) {
        for (StructNode kid : kids()) {
            kid.moveTo(newParent);
        }
    }

    /**
     * Takes this tag, with everything it holds, out of its parent's kids, whether the parent is an
     * element or the root.
     */
    @Override
    public void remove() {
        IStructureNode parent = elem.getParent();
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

    /** Puts the replacements where this tag sits, in order, and takes this tag out of the tree. */
    public void replaceWith(List<Tag> replacements) {
        requireAttached();
        for (Tag replacement : replacements) {
            replacement.insertBefore(this);
        }
        remove();
    }

    /**
     * Removes this tag if it holds nothing, then each ancestor that leaves empty, stopping short of
     * the top level. Returns how many tags were removed.
     */
    public int pruneIfEmpty() {
        int removed = 0;
        Tag current = this;
        while (current.kids().isEmpty()) {
            Tag parent = current.parent();
            if (parent == null) {
                break;
            }
            logger.debug("Pruning empty {}", current);
            current.remove();
            removed++;
            current = parent;
        }
        return removed;
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

    /**
     * Inserts this tag among the sibling's kin, {@code offset} places past the sibling. A tag moved
     * from elsewhere first writes down the page it inherited, which its old ancestors would no
     * longer supply.
     */
    private void insertBeside(Tag sibling, int offset) {
        sibling.requireAttached();
        IStructureNode container = sibling.elem.getParent();
        IStructureNode former = elem.getParent();
        if (former != null) {
            materializeInheritedPage();
            remove();
        }
        // Looked up only now: leaving the same parent shifts the sibling's position.
        int index = sibling.indexInParent() + offset;
        switch (container) {
            case PdfStructElem parent -> parent.addKid(index, elem);
            case PdfStructTreeRoot root -> root.addKid(index, elem);
            default -> throw new IllegalStateException("Unexpected container " + container);
        }
    }

    /**
     * Takes a node out of its parent to become this tag's kid at {@code index}, or its last kid
     * when the index is negative, keeping the node on the page it renders on. Delegates to iText's
     * {@link TagTreePointer#relocateKid}, which rebinds the node to its new parent and, for marked
     * content or an object reference, upgrades a bare MCID number to a dictionary carrying its own
     * /Pg whenever this tag sits on a different page.
     *
     * @throws IllegalArgumentException if the node's parent does not list it among its kids
     */
    void adopt(IStructureNode kid, int index) {
        Tag former = Tag.of((PdfStructElem) kid.getParent());
        int from = indexOf(former.elem, kid);
        if (from < 0) {
            throw new IllegalArgumentException(
                    "Cannot move " + Format.node(kid) + " out of " + former);
        }
        // iText resolves an MCR's page from its immediate parent only and
        // dereferences the result unguarded, while a tagger may leave /Pg on a
        // more distant ancestor. Writing the inherited value down is a no-op
        // for readers.
        former.materializeInheritedPage();
        materializeInheritedPage();
        TagStructureContext context = former.document().getTagStructureContext();
        TagTreePointer destination = context.createPointerForStructElem(elem);
        if (index >= 0) {
            destination.setNextNewKidIndex(index);
        }
        context.createPointerForStructElem(former.elem).relocateKid(from, destination);
    }

    /**
     * Takes marked content or an object reference out of this tag's kids and out of the parent
     * tree. iText unregisters it by dereferencing its page unguarded, so an inherited /Pg is first
     * written down; one that still resolves no page was never registered, and is only cut out.
     *
     * @throws IllegalArgumentException if this tag does not hold the content
     */
    void removeContent(PdfMcr content) {
        int index = indexOf(elem, content);
        if (index < 0) {
            throw new IllegalArgumentException(Format.node(content) + " is not held by " + this);
        }
        materializeInheritedPage();
        elem.removeKid(index, content.getPageIndirectReference() == null);
    }

    private static IStructureNode iTextNodeOf(StructNode node) {
        return switch (node) {
            case Tag tag -> tag.elem;
            case MarkedContent content -> content.mcr();
            case ObjectRef ref -> ref.objRef();
        };
    }

    private void requireAttached() {
        if (!isAttached()) {
            throw new IllegalStateException(this + " is not among its parent's kids");
        }
    }

    /** This tag's position among its parent's kids of every kind, or -1 if it is not there. */
    private int indexInParent() {
        IStructureNode container = elem.getParent();
        return container == null ? -1 : indexOf(container, elem);
    }

    /** Its parent's kids of every kind, this tag included. */
    private List<StructNode> siblings() {
        return kidsOf(elem.getParent()).stream().map(StructNode::of).toList();
    }

    /** Direct kids of the given type. */
    @SuppressWarnings("unchecked")
    <T extends IStructureNode> List<T> kids(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (IStructureNode kid : kidsOf(elem)) {
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
        for (IStructureNode kid : kidsOf(elem)) {
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

    /** A parent's kids, or an empty list where getKids reports none. */
    private static List<IStructureNode> kidsOf(IStructureNode parent) {
        List<IStructureNode> kids = parent.getKids();
        return kids == null ? List.of() : kids;
    }

    /**
     * The position of a kid among a parent's kids, or -1. Matching is by underlying PDF object, so
     * the kid must come from the tree rather than be a freshly constructed wrapper: a bare MCID
     * number is a direct object, for which identity is the only available test.
     */
    private static int indexOf(IStructureNode parent, IStructureNode kid) {
        PdfObject target = pdfObjectOf(kid);
        List<IStructureNode> kids = kidsOf(parent);
        for (int i = 0; i < kids.size(); i++) {
            if (StructTree.isSame(pdfObjectOf(kids.get(i)), target)) {
                return i;
            }
        }
        return -1;
    }

    private static PdfObject pdfObjectOf(IStructureNode node) {
        return switch (node) {
            case PdfStructElem elem -> elem.getPdfObject();
            case PdfMcr mcr -> mcr.getPdfObject();
            default -> throw new IllegalArgumentException("Not a kid: " + node.getClass());
        };
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

    private PdfDocument document() {
        return elem.getPdfObject().getIndirectReference().getDocument();
    }
}
