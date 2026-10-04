// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rebuilds a list from the outline its bullets set, discarding whatever list structure the content
 * sat in before. The content elements are the pieces and the outline is the plan (GoF Builder,
 * whose "parts" are called pieces here so as not to clash with PDF/UA's Part role): each bulleted
 * element becomes the content of a fresh LI &gt; LBody at its bullet's depth, a sublist opens
 * inside the LBody of the item it belongs under, and an unbulleted element continuing an item joins
 * that item's LBody.
 *
 * <p>An element lumping several bullets is first split into one element per bullet by {@link
 * SplitIntoListItemsFix}. Every L, LI and LBody wrapper holding the list's content is then
 * flattened: all it held — the list's content and anything else mistagged into it, such as a
 * heading — stands in its place, in order, and the new list is built where the first item stands.
 */
public final class RebuildListFix implements IssueFix {

    private static final Logger logger = LoggerFactory.getLogger(RebuildListFix.class);

    /** Roles of the list wrappers the rebuild flattens. */
    public static final Set<String> WRAPPER_ROLES = Set.of("L", "LI", "LBody");

    /** Depth marking an element that continues the item before it rather than opening one. */
    public static final int CONTINUATION = -1;

    /**
     * One content element of the list, in reading order: the outline depth of each item it opens
     * (none for a continuation), the per-item line counts when it opens several, and its page.
     */
    public record Piece(PdfStructElem element, int page, List<Integer> depths, String itemSpec) {}

    /** A content element placed in the outline: the depth of its item, or a continuation. */
    private record Placed(PdfStructElem element, int page, int depth) {}

    private final List<Piece> pieces;

    public RebuildListFix(List<Piece> pieces) {
        this.pieces = List.copyOf(pieces);
    }

    @Override
    public int priority() {
        return 40; // last: the rebuild starts from whatever the targeted list fixes left
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        List<Placed> placed = splitIntoItems(ctx);
        PdfStructElem host =
                outsideListWrappers(
                        StructTree.commonAncestorOf(placed.stream().map(Placed::element).toList()));
        if (host == null) {
            throw new IllegalStateException("List content has no common container");
        }

        for (Placed placement : placed) {
            PdfStructElem outermost = kidOfHostHolding(host, placement.element());
            if (WRAPPER_ROLES.contains(StructTree.mappedRole(outermost))) {
                flatten(host, outermost, ctx);
            }
        }

        ListAssembler assembler = new ListAssembler(ctx.doc());
        PdfStructElem list =
                assembler.newListAt(host, StructTree.findKidIndex(host, placed.get(0).element()));
        List<PdfStructElem> openLists = new ArrayList<>(List.of(list));
        List<PdfStructElem> openBodies = new ArrayList<>();
        for (Placed placement : placed) {
            PdfPage page = ctx.doc().getPage(placement.page());
            if (placement.depth() == CONTINUATION) {
                assembler.adoptIntoBody(
                        openBodies.get(openBodies.size() - 1), placement.element(), page);
                continue;
            }
            int depth = placement.depth();
            truncate(openBodies, depth);
            truncate(openLists, depth + 1);
            if (openLists.size() == depth) {
                openLists.add(assembler.newListIn(openBodies.get(depth - 1)));
            }
            PdfStructElem lBody = assembler.newItemBody(openLists.get(depth));
            assembler.adoptIntoBody(lBody, placement.element(), page);
            openBodies.add(lBody);
        }
        ListItemScribble.update(list, "REBUILD outline");

        logger.debug(
                "Rebuilt list of {} elements from its outline under #{}",
                placed.size(),
                StructTree.objNum(host));
    }

    /**
     * Places every piece in the outline, first splitting each one that lumps several items. The
     * split leaves its items as consecutive LIs after the piece's own, which is where they are
     * collected from.
     */
    private List<Placed> splitIntoItems(DocContext ctx) throws Exception {
        List<Placed> placed = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.element().getParent() == null) {
                throw new IllegalStateException(
                        "Element #"
                                + StructTree.objNum(piece.element())
                                + " is no longer in the tree");
            }
            if (piece.depths().isEmpty()) {
                placed.add(new Placed(piece.element(), piece.page(), CONTINUATION));
                continue;
            }
            if (piece.depths().size() == 1) {
                placed.add(new Placed(piece.element(), piece.page(), piece.depths().get(0)));
                continue;
            }

            new SplitIntoListItemsFix(piece.element(), piece.itemSpec()).apply(ctx);
            List<PdfStructElem> items = itemsSplitFrom(piece.element(), piece.depths().size());
            for (int i = 0; i < items.size(); i++) {
                placed.add(new Placed(items.get(i), piece.page(), piece.depths().get(i)));
            }
        }
        return placed;
    }

    /** Returns the content of the {@code count} consecutive items starting with the element's. */
    private static List<PdfStructElem> itemsSplitFrom(PdfStructElem element, int count) {
        PdfStructElem li = StructTree.parentOf(StructTree.parentOf(element));
        PdfStructElem list = StructTree.parentOf(li);
        List<PdfStructElem> items = StructTree.childrenOf(list, PdfStructElem.class);
        int first = StructTree.findKidIndex(list, li);
        List<PdfStructElem> contents = new ArrayList<>();
        for (PdfStructElem item : items.subList(first, first + count)) {
            PdfStructElem lBody = StructTree.childrenOf(item, PdfStructElem.class).get(0);
            contents.add(StructTree.childrenOf(lBody, PdfStructElem.class).get(0));
        }
        return contents;
    }

    /** Closes everything open deeper than the given size: items replaced, sublists ended. */
    private static void truncate(List<PdfStructElem> open, int size) {
        if (open.size() > size) {
            open.subList(size, open.size()).clear();
        }
    }

    /** Climbs out of the list wrappers being replaced to the element that holds them. */
    private static PdfStructElem outsideListWrappers(PdfStructElem element) {
        PdfStructElem elem = element;
        while (elem != null && WRAPPER_ROLES.contains(StructTree.mappedRole(elem))) {
            elem = StructTree.parentOf(elem);
        }
        return elem;
    }

    /** Returns the host's kid that is, or contains, the element. */
    private static PdfStructElem kidOfHostHolding(PdfStructElem host, PdfStructElem element) {
        PdfStructElem kid = element;
        while (!StructTree.isSameElement(StructTree.parentOf(kid), host)) {
            kid = StructTree.parentOf(kid);
        }
        return kid;
    }

    /**
     * Replaces a list wrapper in the host with the content it holds, in reading order. Nested
     * wrappers are flattened along with it; marked content or a label held directly by a wrapper
     * has no element to stand on its own, so the rebuild refuses it.
     */
    private static void flatten(PdfStructElem host, PdfStructElem wrapper, DocContext ctx) {
        List<PdfStructElem> content = new ArrayList<>();
        collectContent(wrapper, content);
        int index = StructTree.findKidIndex(host, wrapper);
        for (PdfStructElem elem : content) {
            int pageNum = StructTree.pageOf(elem, ctx);
            if (pageNum > 0) {
                ListAssembler.pinPage(elem, ctx.doc().getPage(pageNum));
            }
            StructTree.parentOf(elem).removeKid(elem);
            host.addKid(index++, elem);
        }
        host.removeKid(wrapper);
    }

    /** Gathers the content elements under a wrapper, descending through nested wrappers. */
    private static void collectContent(PdfStructElem wrapper, List<PdfStructElem> content) {
        for (IStructureNode kid : StructTree.kidsOf(wrapper)) {
            if (!(kid instanceof PdfStructElem elem) || "Lbl".equals(StructTree.mappedRole(elem))) {
                throw new IllegalStateException(
                        StructTree.mappedRole(wrapper)
                                + " #"
                                + StructTree.objNum(wrapper)
                                + " holds marked content or a label of its own");
            }
            if (WRAPPER_ROLES.contains(StructTree.mappedRole(elem))) {
                collectContent(elem, content);
            } else {
                content.add(elem);
            }
        }
    }

    @Override
    public String describe() {
        return "Rebuilt list from the outline its bullets set";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, pieces.get(0).element()));
    }

    @Override
    public String groupLabel() {
        return "lists rebuilt from their bullets";
    }
}
