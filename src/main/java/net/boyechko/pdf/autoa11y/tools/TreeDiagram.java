// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Label;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.TreeNode;
import net.boyechko.pdf.autoa11y.document.TreeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renders PDF structure trees as text diagrams and applies annotations from edited dumps. */
public final class TreeDiagram {
    private static final Logger logger = LoggerFactory.getLogger(TreeDiagram.class);

    /**
     * Matches a struct-element line in a tree diagram: optional box-drawing/space prefix, then
     * "Role #objNum" with an optional trailing quoted scribble. The {@code (?<!<)} lookbehind
     * prevents matching OBJR lines, where the label appears inside {@code <...>}.
     */
    private static final Pattern ANNOTATED_LINE =
            Pattern.compile("^\\W*(?<!<)(\\w+)\\s+#(\\d+)(?:\\s+\"([^\"]*)\")?.*$");

    /** Roles whose text is shown untruncated, since it identifies the element. */
    private static final Set<String> SHOWN_IN_FULL =
            Set.of("Link", "H1", "H2", "H3", "H4", "H5", "H6");

    /**
     * Line-prefix style for detailed tree diagrams. {@link #BOX} is the human-facing default;
     * {@link #PLAIN} is the stable machine-facing format used for goal snapshots, immune to
     * cosmetic changes in the box-drawing rendering.
     */
    public enum Style {
        BOX("├── ", "└── ", "│   ", "    "),
        PLAIN("  ", "  ", "  ", "  ");

        private final String selfMiddle;
        private final String selfFinal;
        private final String leafMiddle;
        private final String leafFinal;

        Style(String selfMiddle, String selfFinal, String leafMiddle, String leafFinal) {
            this.selfMiddle = selfMiddle;
            this.selfFinal = selfFinal;
            this.leafMiddle = leafMiddle;
            this.leafFinal = leafFinal;
        }

        String selfPrefix(boolean hasMore) {
            return hasMore ? selfMiddle : selfFinal;
        }

        String leafPrefix(boolean hasMore) {
            return hasMore ? leafMiddle : leafFinal;
        }
    }

    private TreeDiagram() {}

    /**
     * Renders the structure tree of a PDF document as text. When {@code detailed} is true, includes
     * MCRs, OBJRs, and content-kind labels; otherwise renders role names only.
     *
     * @throws IllegalStateException if the document has no structure tree
     */
    public static String dumpToString(PdfDocument pdfDoc, boolean detailed) {
        return dumpToString(pdfDoc, detailed, Style.BOX);
    }

    /** Like {@link #dumpToString(PdfDocument, boolean)}, with an explicit line-prefix style. */
    public static String dumpToString(PdfDocument pdfDoc, boolean detailed, Style style) {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        if (root == null) {
            throw new IllegalStateException("PDF has no structure tree");
        }
        PdfStructElem docElem = StructTree.findDocument(root);
        IStructureNode start = docElem != null ? docElem : root;
        if (detailed) {
            return toDetailedTreeString(TreeSnapshot.capture(pdfDoc), style);
        }
        return toIndentedTreeString(start);
    }

    // === Indented tree diagram =============================================

    /**
     * Converts a structure tree into an indented, multi-line string of role names. A {@link
     * PdfStructElem} is rendered as the top line of the output, with its descendants indented
     * below.
     */
    public static String toIndentedTreeString(IStructureNode node) {
        return toIndentedTreeString(TreeSnapshot.capture(node, Map.of()));
    }

    /** Renders the original roles in a snapshot, omitting content children. */
    public static String toIndentedTreeString(TreeNode<TreeSnapshot.Value> tree) {
        StringBuilder sb = new StringBuilder();
        appendIndentedTree(sb, tree, 0);
        return sb.toString();
    }

    private static void appendIndentedTree(
            StringBuilder sb, TreeNode<TreeSnapshot.Value> node, int depth) {
        int childDepth = depth;
        if (node.value() instanceof TreeSnapshot.Element element) {
            sb.repeat("  ", depth);
            sb.append(element.role()).append('\n');
            childDepth++;
        }
        for (var child : node.children()) {
            if (child.value() instanceof TreeSnapshot.Element) {
                appendIndentedTree(sb, child, childDepth);
            }
        }
    }

    /**
     * Like {@link #toIndentedTreeString}, but also shows MCRs and annotation object references
     * (OBJRs) as leaf annotations on each element.
     */
    public static String toDetailedTreeString(IStructureNode node) {
        return toDetailedTreeString(node, Map.of());
    }

    /**
     * Like {@link #toDetailedTreeString(IStructureNode)}, but labels MCRs with their content kind
     * (text/image/path) and text using the provided map keyed by (page, MCID).
     */
    public static String toDetailedTreeString(
            IStructureNode node, Map<Content.PageMcid, Content.McidContent> mcidInfo) {
        return toDetailedTreeString(node, mcidInfo, Style.BOX);
    }

    /**
     * Like {@link #toDetailedTreeString(IStructureNode, Map)}, with an explicit line-prefix style.
     */
    public static String toDetailedTreeString(
            IStructureNode node, Map<Content.PageMcid, Content.McidContent> mcidInfo, Style style) {
        return toDetailedTreeString(TreeSnapshot.capture(node, mcidInfo), style);
    }

    /** Renders a captured tree without accessing the source PDF. */
    public static String toDetailedTreeString(TreeNode<TreeSnapshot.Value> tree, Style style) {
        StringBuilder sb = new StringBuilder();
        int[] currentPage = {0};
        if (tree.value() instanceof TreeSnapshot.Element) {
            appendDetailedTree(sb, tree, "", "", style, currentPage);
        } else {
            var kids = tree.children();
            for (int i = 0; i < kids.size(); i++) {
                if (!(kids.get(i).value() instanceof TreeSnapshot.Element)) continue;
                boolean hasMore = hasStructElemAfter(kids, i);
                appendDetailedTree(
                        sb,
                        kids.get(i),
                        style.selfPrefix(hasMore),
                        style.leafPrefix(hasMore),
                        style,
                        currentPage);
            }
        }
        return sb.toString();
    }

    private static void appendDetailedTree(
            StringBuilder sb,
            TreeNode<TreeSnapshot.Value> node,
            String selfPrefix,
            String childrenPrefix,
            Style style,
            int[] currentPage) {
        var element = (TreeSnapshot.Element) node.value();
        emitPageBreakIfNeeded(sb, element.page(), currentPage);
        sb.append(selfPrefix).append(structElemLabel(element)).append('\n');

        var kids = node.children();
        for (int i = 0; i < kids.size(); i++) {
            boolean hasMore = hasStructElemAfter(kids, i);
            String leafPrefix = style.leafPrefix(hasMore);
            var child = kids.get(i);
            switch (child.value()) {
                case TreeSnapshot.Element ignored ->
                        appendDetailedTree(
                                sb,
                                child,
                                childrenPrefix + style.selfPrefix(hasMore),
                                childrenPrefix + leafPrefix,
                                style,
                                currentPage);
                case TreeSnapshot.ObjectReference ref -> {
                    emitPageBreakIfNeeded(sb, ref.page(), currentPage);
                    sb.append(childrenPrefix).append(leafPrefix);
                    sb.append("<")
                            .append(ref.annotation() != null ? ref.annotation() : "unknown")
                            .append("> ")
                            .append(ref.destination());
                    if (ref.originalUri() != null)
                        sb.append(" [").append(ref.originalUri()).append("]");
                    sb.append('\n');
                }
                case TreeSnapshot.MarkedContent content -> {
                    if (content.orphaned()) {
                        sb.append(childrenPrefix)
                                .append(leafPrefix)
                                .append("[orphaned mcid ")
                                .append(content.mcid())
                                .append("]\n");
                        continue;
                    }
                    emitPageBreakIfNeeded(sb, content.page(), currentPage);
                    sb.append(childrenPrefix)
                            .append(leafPrefix)
                            .append("[")
                            .append(new DocValue.Mcr(content.mcid(), content.kinds()))
                            .append("]");
                    String text =
                            SHOWN_IN_FULL.contains(element.role())
                                    ? content.text()
                                    : Format.truncate(content.text());
                    if (!text.isBlank()) sb.append(" `").append(text).append("'");
                    sb.append('\n');
                }
                case TreeSnapshot.Root ignored ->
                        throw new IllegalArgumentException("Nested synthetic root in snapshot");
            }
        }
    }

    /** Returns whether another structural element follows this child. */
    private static boolean hasStructElemAfter(
            List<TreeNode<TreeSnapshot.Value>> kids, int fromIdx) {
        for (int j = fromIdx + 1; j < kids.size(); j++) {
            if (kids.get(j).value() instanceof TreeSnapshot.Element) return true;
        }
        return false;
    }

    private static void emitPageBreakIfNeeded(StringBuilder sb, int pageNum, int[] currentPage) {
        if (pageNum > 0 && pageNum != currentPage[0]) {
            currentPage[0] = pageNum;
            sb.append("------------------------- Page ");
            sb.append(pageNum);
            sb.append(" ---\n");
        }
    }

    private static String structElemLabel(TreeSnapshot.Element element) {
        return Label.of(new DocValue.Role(element.mappedRole()))
                .add(element.objNum() >= 0 ? new DocValue.ObjNum(element.objNum()) : null)
                .add(element.scribble())
                .add(element.title())
                .add(element.scope())
                .toString();
    }

    // === Annotate tree from tree diagram file =================================

    /** Result of applying scribbles from an annotated tree diagram. */
    public record AnnotateResult(
            int updated, int cleared, int unchanged, int unmatchedLines, int unmatchedElements) {}

    /**
     * Parses an annotated tree diagram and writes quoted scribbles back to the matching elements'
     * {@code /T} keys. Lines without a quoted scribble clear {@code /T} on the matched element.
     * Role mismatches and unknown object numbers are reported via {@code warn}.
     */
    public static AnnotateResult annotateFromString(
            PdfDocument doc, String annotatedDiagram, Consumer<String> warn) {
        Map<ElemKey, Optional<String>> desired = parseDiagram(annotatedDiagram, warn);

        Map<Integer, PdfStructElem> byObj = new HashMap<>();
        PdfStructTreeRoot root = doc.getStructTreeRoot();
        if (root != null) {
            for (IStructureNode kid : root.getKids()) {
                indexByObjNum(kid, byObj);
            }
        }

        int updated = 0, cleared = 0, unchanged = 0, unmatchedLines = 0;

        for (Map.Entry<ElemKey, Optional<String>> entry : desired.entrySet()) {
            ElemKey key = entry.getKey();
            PdfStructElem elem = byObj.get(key.objNum());
            if (elem == null) {
                warn.accept("no element found for " + key.label());
                unmatchedLines++;
                continue;
            }
            String actualRole = StructTree.mappedRole(elem);
            if (!key.role().equals(actualRole)) {
                warn.accept("line says " + key.label() + " but element's role is " + actualRole);
                unmatchedLines++;
                continue;
            }

            DocValue.Scribble existing = DocValue.Scribble.of(elem);
            String existingRaw = existing != null ? existing.rawValue() : null;
            Optional<String> newScribble = entry.getValue();
            if (newScribble.isPresent()) {
                if (newScribble.get().equals(existingRaw)) {
                    unchanged++;
                } else {
                    if (existing == null) {
                        logger.debug("set {}: {}", key.label(), newScribble.get());
                    } else {
                        logger.debug(
                                "update {}: {} -> {}",
                                key.label(),
                                existing.value(),
                                newScribble.get());
                    }
                    elem.put(PdfName.T, new PdfString(newScribble.get()));
                    updated++;
                }
            } else if (existing != null) {
                // Only a scribble the dump actually renders is clearable. A /T that is empty,
                // control-char-only, or a plain title shows as nothing (DocValue.Scribble.of
                // returns null), so treating it as clearable would make a no-op round-trip
                // silently mutate.
                logger.debug("clear {}: {}", key.label(), existing.value());
                elem.getPdfObject().remove(PdfName.T);
                cleared++;
            } else {
                unchanged++;
            }
        }

        int unmatchedElements = 0;
        for (Map.Entry<Integer, PdfStructElem> e : byObj.entrySet()) {
            String role = StructTree.mappedRole(e.getValue());
            if (!desired.containsKey(new ElemKey(role, e.getKey()))) {
                unmatchedElements++;
            }
        }

        return new AnnotateResult(updated, cleared, unchanged, unmatchedLines, unmatchedElements);
    }

    /**
     * Parses an annotated tree diagram into a map of element keys to desired scribble values.
     *
     * <p>Each non-blank line is matched against {@link #ANNOTATED_LINE}. Lines that match but carry
     * no quoted scribble produce {@code Optional.empty()}, signalling that {@code /T} should be
     * cleared on the matched element. Lines that do not match the pattern are silently skipped.
     * Duplicate keys with conflicting scribble values are reported via {@code warn}.
     *
     * @param content text of an annotated tree diagram, possibly with added quoted scribbles
     * @param warn callback invoked with a human-readable message when the same element key appears
     *     more than once with differing scribble values
     * @return map from each parsed {@link ElemKey} to its desired scribble ({@code
     *     Optional.empty()} means clear {@code /T}); elements not mentioned in the diagram are
     *     absent from the map
     */
    private static Map<ElemKey, Optional<String>> parseDiagram(
            String content, Consumer<String> warn) {
        Map<ElemKey, Optional<String>> out = new HashMap<>();
        List<String> lines = content.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;

            Matcher m = ANNOTATED_LINE.matcher(line);
            if (!m.matches()) continue;

            String role = m.group(1);
            int objNum = Integer.parseInt(m.group(2));
            String scribble = m.group(3);

            ElemKey key = new ElemKey(role, objNum);
            Optional<String> value = scribble != null ? Optional.of(scribble) : Optional.empty();
            Optional<String> prior = out.put(key, value);
            if (prior != null && !prior.equals(value)) {
                warn.accept("duplicate line for " + key.label() + " (line " + (i + 1) + ")");
            }
        }
        return out;
    }

    /**
     * Recursively indexes all {@link PdfStructElem} nodes in a subtree by indirect object number.
     */
    private static void indexByObjNum(IStructureNode node, Map<Integer, PdfStructElem> out) {
        if (node instanceof PdfStructElem elem) {
            int objNum = StructTree.objNum(elem);
            if (objNum > 0) out.put(objNum, elem);
        }
        List<IStructureNode> kids = node.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid != null) indexByObjNum(kid, out);
            }
        }
    }

    /** Structural element key consisting of its role and indirect reference object number. */
    private record ElemKey(String role, int objNum) {
        String label() {
            return Label.of(new DocValue.Role(role)).add(new DocValue.ObjNum(objNum)).toString();
        }
    }
}
