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

import static net.boyechko.pdf.autoa11y.document.StructTree.childrenOf;
import static net.boyechko.pdf.autoa11y.document.StructTree.pageOf;

import com.itextpdf.kernel.pdf.tagging.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renders PDF structure trees as text diagrams. */
public final class TreeDiagram {
    private static final Logger logger = LoggerFactory.getLogger(TreeDiagram.class);

    private TreeDiagram() {}

    // === Indented tree diagram =============================================

    /** Converts a PdfStructElem tree into an indented, multi-line string of role names. */
    public static String toIndentedTreeString(PdfStructElem elem) {
        StringBuilder sb = new StringBuilder();
        appendIndentedTree(sb, elem, 0);
        return sb.toString();
    }

    private static void appendIndentedTree(StringBuilder sb, PdfStructElem elem, int depth) {
        sb.append("  ".repeat(depth));
        sb.append(elem.getRole().getValue());
        sb.append('\n');
        for (PdfStructElem kid : childrenOf(elem, PdfStructElem.class)) {
            appendIndentedTree(sb, kid, depth + 1);
        }
    }

    /**
     * Like {@link #toIndentedTreeString}, but also shows MCRs and annotation object references
     * (OBJRs) as leaf annotations on each element.
     */
    public static String toDetailedTreeString(PdfStructElem elem) {
        return toDetailedTreeString(elem, Map.of());
    }

    /**
     * Like {@link #toDetailedTreeString(PdfStructElem)}, but labels MCRs with their content kind
     * (text/image) using the provided map from MCID to content kinds.
     */
    public static String toDetailedTreeString(
            PdfStructElem elem, Map<Integer, Set<Content.ContentKind>> contentKinds) {
        StringBuilder sb = new StringBuilder();
        int[] currentPage = {0};
        appendDetailedTree(sb, elem, 0, contentKinds, currentPage);
        return sb.toString();
    }

    private static void appendDetailedTree(
            StringBuilder sb,
            PdfStructElem elem,
            int depth,
            Map<Integer, Set<Content.ContentKind>> contentKinds,
            int[] currentPage) {
        // Emit page break before the element if its first leaf is on a new page
        emitPageBreakIfNeeded(sb, firstLeafPage(elem), currentPage);

        sb.append(indentation(depth));
        sb.append(structElemLabel(elem));
        sb.append('\n');

        // Output all children in /K array order
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return;

        String childIndent = indentation(depth + 1);
        for (IStructureNode kid : kids) {
            switch (kid) {
                case PdfStructElem childElem ->
                        appendDetailedTree(sb, childElem, depth + 1, contentKinds, currentPage);
                case PdfObjRef objRef -> {
                    emitPageBreakIfNeeded(sb, pageOf(objRef), currentPage);
                    sb.append(childIndent);
                    sb.append("<" + objrLabel(objRef) + ">");
                    sb.append('\n');
                }
                case PdfMcr mcr -> {
                    emitPageBreakIfNeeded(sb, pageOf(mcr), currentPage);
                    sb.append(childIndent);
                    sb.append(
                            "["
                                    + new DocValue.Mcr(
                                            mcr.getMcid(), contentKinds.get(mcr.getMcid()))
                                    + "]");
                    sb.append('\n');
                }
                default -> throw new IllegalArgumentException("Unexpected value: " + kid);
            }
        }
    }

    /** Returns the page number of the first MCR or OBJR leaf in a subtree, or 0 if none. */
    private static int firstLeafPage(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return 0;
        for (IStructureNode kid : kids) {
            switch (kid) {
                case PdfMcr mcr -> {
                    int page = pageOf(mcr);
                    if (page > 0) return page;
                }
                case PdfStructElem childElem -> {
                    int page = firstLeafPage(childElem);
                    if (page > 0) return page;
                }
                default -> {}
            }
        }
        return 0;
    }

    private static void emitPageBreakIfNeeded(StringBuilder sb, int pageNum, int[] currentPage) {
        if (pageNum > 0 && pageNum != currentPage[0]) {
            currentPage[0] = pageNum;
            sb.append("------------------------- Page ");
            sb.append(pageNum);
            sb.append(" ---\n");
        }
    }

    private static String structElemLabel(PdfStructElem elem) {
        return Label.of(new DocValue.Role(StructTree.mappedRole(elem)))
                .add(DocValue.ObjNum.of(elem))
                .add(DocValue.Scribble.of(elem))
                .toString();
    }

    private static String objrLabel(PdfObjRef objRef) {
        DocValue annot = DocValue.annotOf(objRef);
        if (annot == null) {
            logger.debug(
                    "No referenced object found for OBJR {}", objRef.getPdfObject().toString());
            return "unknown";
        }
        return annot.toString();
    }

    private static String indentation(int depth) {
        return " ".repeat(2 * depth);
    }

    // === Render structure tree as a string =================================

    public record Node<T>(T value, List<Node<T>> children) {
        /** Creates a leaf node (no children). */
        public static <T> Node<T> leaf(T value) {
            return new Node<>(value, List.of());
        }

        /** Creates a node with children. */
        @SafeVarargs
        public static <T> Node<T> branch(T value, Node<T>... children) {
            return new Node<>(value, List.of(children));
        }

        /**
         * Parses bracket notation like {@code Reference[Lbl[], Span[]]} into a list of Node trees.
         * This is the inverse of {@link #toString()}.
         */
        public static List<Node<String>> fromString(String expr) {
            List<Node<String>> nodes = new ArrayList<>();
            int[] pos = {0};
            parseNodes(expr, pos, nodes);
            return nodes;
        }

        private static void parseNodes(String expr, int[] pos, List<Node<String>> out) {
            while (pos[0] < expr.length()) {
                // Skip whitespace and commas
                while (pos[0] < expr.length()
                        && (expr.charAt(pos[0]) == ' ' || expr.charAt(pos[0]) == ',')) {
                    pos[0]++;
                }
                if (pos[0] >= expr.length() || expr.charAt(pos[0]) == ']') {
                    break;
                }

                // Parse tag name up to '[', ']', ',' or end
                int nameStart = pos[0];
                while (pos[0] < expr.length()
                        && expr.charAt(pos[0]) != '['
                        && expr.charAt(pos[0]) != ']'
                        && expr.charAt(pos[0]) != ',') {
                    pos[0]++;
                }
                String tagName = expr.substring(nameStart, pos[0]).trim();
                if (tagName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Invalid tag expression at position " + pos[0]);
                }

                // If followed by '[', parse children; otherwise it's a leaf
                List<Node<String>> children = new ArrayList<>();
                if (pos[0] < expr.length() && expr.charAt(pos[0]) == '[') {
                    pos[0]++; // skip '['
                    parseNodes(expr, pos, children);
                    if (pos[0] < expr.length() && expr.charAt(pos[0]) == ']') {
                        pos[0]++;
                    }
                }

                out.add(new Node<>(tagName, children));
            }
        }

        /** Returns bracket notation, e.g. {@code Document[L[LI[Lbl[], LBody[P[]]]]]}. */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(value).append('[');
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(children.get(i));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    /** Converts a PdfStructElem tree into a Node tree of role-name strings. */
    public static Node<String> toRoleTree(PdfStructElem elem) {
        List<Node<String>> childNodes = new ArrayList<>();
        for (PdfStructElem kid : childrenOf(elem, PdfStructElem.class)) {
            childNodes.add(toRoleTree(kid));
        }
        return new Node<>(elem.getRole().getValue(), childNodes);
    }

    /** Converts a PdfStructElem tree into a Node tree of role-name strings. */
    public static String toRoleTreeString(PdfStructElem elem) {
        return toRoleTree(elem).toString().replaceAll("\\s+", "");
    }

    /** Convenience wrapper for {@link Node#fromString(String)}. */
    public static List<Node<String>> fromRoleTreeString(String expr) {
        return Node.fromString(expr);
    }
}
