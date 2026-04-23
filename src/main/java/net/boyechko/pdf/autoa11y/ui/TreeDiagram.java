/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.ui;

import static net.boyechko.pdf.autoa11y.document.StructTree.childrenOf;
import static net.boyechko.pdf.autoa11y.document.StructTree.pageOf;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.Label;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renders PDF structure trees as text diagrams and applies annotations from edited dumps. */
public final class TreeDiagram {
    private static final Logger logger = LoggerFactory.getLogger(TreeDiagram.class);

    /** Matches "Role #objNum" with an optional trailing quoted scribble. */
    private static final Pattern ANNOTATED_LINE =
            Pattern.compile("^\\s*(\\w+)\\s+#(\\d+)(?:\\s+\"([^\"]*)\")?.*$");

    private TreeDiagram() {}

    /**
     * Renders the structure tree of a PDF document as text. When {@code detailed} is true, includes
     * MCRs, OBJRs, and content-kind labels; otherwise renders role names only.
     *
     * @throws IllegalStateException if the document has no structure tree
     */
    public static String dumpToString(PdfDocument pdfDoc, boolean detailed) {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        if (root == null) {
            throw new IllegalStateException("PDF has no structure tree");
        }
        PdfStructElem docElem = StructTree.findDocument(root);
        IStructureNode start = docElem != null ? docElem : root;
        if (detailed) {
            Map<Integer, Set<Content.ContentKind>> contentKinds = new HashMap<>();
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                contentKinds.putAll(Content.extractContentKindsForPage(page));
            }
            return toDetailedTreeString(start, contentKinds);
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
        StringBuilder sb = new StringBuilder();
        if (node instanceof PdfStructElem elem) {
            appendIndentedTree(sb, elem, 0);
        } else {
            for (IStructureNode kid : node.getKids()) {
                if (kid instanceof PdfStructElem childElem) {
                    appendIndentedTree(sb, childElem, 0);
                }
            }
        }
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
    public static String toDetailedTreeString(IStructureNode node) {
        return toDetailedTreeString(node, Map.of());
    }

    /**
     * Like {@link #toDetailedTreeString(IStructureNode)}, but labels MCRs with their content kind
     * (text/image) using the provided map from MCID to content kinds.
     */
    public static String toDetailedTreeString(
            IStructureNode node, Map<Integer, Set<Content.ContentKind>> contentKinds) {
        StringBuilder sb = new StringBuilder();
        int[] currentPage = {0};
        if (node instanceof PdfStructElem elem) {
            appendDetailedTree(sb, elem, 0, contentKinds, currentPage);
        } else {
            for (IStructureNode kid : node.getKids()) {
                if (kid instanceof PdfStructElem childElem) {
                    appendDetailedTree(sb, childElem, 0, contentKinds, currentPage);
                }
            }
        }
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

    // === Annotate tree from edited dump file =================================

    /** Result of applying annotations from a dump-tree file. */
    public record AnnotateResult(
            int updated, int cleared, int unchanged, int unmatchedLines, int unmatchedElements) {}

    /**
     * Parses an edited {@code --dump-tree} output and writes quoted scribbles back to the matching
     * elements' {@code /T} keys. Lines without a quoted scribble clear {@code /T} on the matched
     * element. Role mismatches and unknown object numbers are reported via {@code warn}.
     */
    public static AnnotateResult annotateFromString(
            PdfDocument doc, String annotatedDump, Consumer<String> warn) {
        Map<ElemKey, Optional<String>> desired = parseDump(annotatedDump, warn);

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
                warn.accept("no element found for " + key.role() + " #" + key.objNum());
                unmatchedLines++;
                continue;
            }
            String actualRole = StructTree.mappedRole(elem);
            if (!key.role().equals(actualRole)) {
                warn.accept(
                        "line says "
                                + key.role()
                                + " #"
                                + key.objNum()
                                + " but element's role is "
                                + actualRole);
                unmatchedLines++;
                continue;
            }

            Optional<String> scribble = entry.getValue();
            if (scribble.isPresent()) {
                elem.put(PdfName.T, new PdfString(scribble.get()));
                updated++;
            } else if (elem.getPdfObject().containsKey(PdfName.T)) {
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

    private static Map<ElemKey, Optional<String>> parseDump(String content, Consumer<String> warn) {
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
                warn.accept(
                        "duplicate line for " + role + " #" + objNum + " (line " + (i + 1) + ")");
            }
        }
        return out;
    }

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

    private record ElemKey(String role, int objNum) {}
}
