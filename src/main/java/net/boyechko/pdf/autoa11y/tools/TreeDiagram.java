// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import static net.boyechko.pdf.autoa11y.document.StructTree.childrenOf;
import static net.boyechko.pdf.autoa11y.document.StructTree.pageOf;

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
    private static final Set<PdfName> SHOWN_IN_FULL =
            Set.of(
                    PdfName.Link,
                    PdfName.H1,
                    PdfName.H2,
                    PdfName.H3,
                    PdfName.H4,
                    PdfName.H5,
                    PdfName.H6);

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
            return toDetailedTreeString(start, gatherMcidInfo(pdfDoc), style);
        }
        return toIndentedTreeString(start);
    }

    /** Gathers content (text, spans, kinds) for every MCID on every page of a PDF document. */
    private static Map<Content.PageMcid, Content.McidContent> gatherMcidInfo(PdfDocument pdfDoc) {
        Map<Content.PageMcid, Content.McidContent> mcidInfo = new HashMap<>();
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            int pageNum = i;
            Content.extractContentForPage(pdfDoc.getPage(i))
                    .forEach((mcid, mc) -> mcidInfo.put(new Content.PageMcid(pageNum, mcid), mc));
        }
        return mcidInfo;
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
        sb.repeat("  ", depth);
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
        StringBuilder sb = new StringBuilder();
        int[] currentPage = {0};
        if (node instanceof PdfStructElem elem) {
            appendDetailedTree(sb, elem, "", "", style, mcidInfo, currentPage);
        } else {
            List<IStructureNode> kids = node.getKids();
            for (int i = 0; i < kids.size(); i++) {
                if (!(kids.get(i) instanceof PdfStructElem childElem)) continue;
                boolean hasMore = hasStructElemAfter(kids, i);
                appendDetailedTree(
                        sb,
                        childElem,
                        style.selfPrefix(hasMore),
                        style.leafPrefix(hasMore),
                        style,
                        mcidInfo,
                        currentPage);
            }
        }
        return sb.toString();
    }

    private static void appendDetailedTree(
            StringBuilder sb,
            PdfStructElem elem,
            String selfPrefix,
            String childrenPrefix,
            Style style,
            Map<Content.PageMcid, Content.McidContent> mcidInfo,
            int[] currentPage) {
        emitPageBreakIfNeeded(sb, firstLeafPage(elem), currentPage);
        sb.append(selfPrefix).append(structElemLabel(elem)).append('\n');

        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return;

        for (int i = 0; i < kids.size(); i++) {
            boolean hasMore = hasStructElemAfter(kids, i);
            String leafPrefix = style.leafPrefix(hasMore);

            switch (kids.get(i)) {
                case PdfStructElem childElem -> {
                    appendDetailedTree(
                            sb,
                            childElem,
                            childrenPrefix + style.selfPrefix(hasMore),
                            childrenPrefix + leafPrefix,
                            style,
                            mcidInfo,
                            currentPage);
                }
                case PdfObjRef objRef -> {
                    emitPageBreakIfNeeded(sb, pageOf(objRef), currentPage);
                    sb.append(childrenPrefix).append(leafPrefix);
                    sb.append("<").append(objrLabel(objRef)).append(">");
                    DocValue.Destination dest = DocValue.destinationOf(objRef);
                    sb.append(' ').append(dest);
                    DocValue.Destination originalUri = DocValue.originalUriOf(objRef);
                    if (originalUri != null) {
                        sb.append(" [").append(originalUri).append("]");
                    }
                    sb.append('\n');
                }
                case PdfMcr mcr -> {
                    if (mcr.getPageIndirectReference() == null) {
                        sb.append(childrenPrefix).append(leafPrefix);
                        sb.append("[orphaned mcid ").append(mcr.getMcid()).append("]");
                        sb.append('\n');
                        continue;
                    }
                    Content.PageMcid pageMcid = new Content.PageMcid(pageOf(mcr), mcr.getMcid());
                    Content.McidContent mc = mcidInfo.get(pageMcid);
                    DocValue.Mcr mcrLabel =
                            new DocValue.Mcr(pageMcid.mcid(), mc != null ? mc.kinds() : null);
                    emitPageBreakIfNeeded(sb, pageMcid.pageNum(), currentPage);
                    sb.append(childrenPrefix).append(leafPrefix);
                    sb.append("[").append(mcrLabel).append("]");
                    String text = mc != null ? mc.text() : "";
                    if (!SHOWN_IN_FULL.contains(elem.getRole())) {
                        text = Format.truncate(text);
                    }
                    if (!text.isBlank()) sb.append(" `").append(text).append("'");
                    sb.append('\n');
                }
                default -> throw new IllegalArgumentException("Unexpected value: " + kids.get(i));
            }
        }
    }

    /** Returns true if a {@link PdfStructElem} appears after {@code fromIdx} in {@code kids}. */
    private static boolean hasStructElemAfter(List<IStructureNode> kids, int fromIdx) {
        for (int j = fromIdx + 1; j < kids.size(); j++) {
            if (kids.get(j) instanceof PdfStructElem) return true;
        }
        return false;
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
                // Only a scribble the dump actually renders is clearable. An empty or
                // control-char-only /T shows as nothing (DocValue.Scribble.of returns null),
                // so treating it as clearable would make a no-op round-trip silently mutate.
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
