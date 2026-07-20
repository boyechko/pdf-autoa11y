// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import static java.lang.Float.NaN;

import com.itextpdf.kernel.pdf.IPdfNameTreeAccess;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps a PDF's outline (bookmarks) to a flat, indented text format and applies edited dumps back
 * to replace {@code /Catalog/Outlines} wholesale. The format is documented in {@code
 * docs/outline-editor.md}.
 *
 * <p>Round-trip: {@code dumpToString} → user edits in $EDITOR → {@code applyFromString}. The apply
 * step is replacive: the entire existing outline is removed before the new one is built.
 *
 * <p>Destinations are always emitted as {@code [page /FitH top]} where {@code top} is the page's
 * MediaBox upper edge. Source-document zoom factors, colors, bold/italic flags, and named
 * destinations are not preserved across the round-trip. Bookmarks whose destination cannot be
 * resolved to a page are emitted with {@code @?} as the page marker; the parser rejects {@code @?}
 * on apply so the user must explicitly assign a page or delete the entry.
 */
public final class OutlineEditor {

    private static final int INDENT_SPACES = 2;

    /**
     * Recognizes the trailing page sigil: one or more spaces, then {@code @<digits>} or {@code @?},
     * then optional trailing whitespace, anchored to end of line.
     */
    private static final Pattern PAGE_SIGIL = Pattern.compile("(.*?)\\s+@(\\?|\\d+)\\s*$");

    private OutlineEditor() {}

    // === Dump =================================================================

    /**
     * Renders the document's outline to text. Returns an empty string if the document has no
     * outline. Each line is {@code <indent><title> @<page>}, where indent is 2 spaces per level of
     * nesting and page is the resolved 1-based page number of the bookmark's destination. Bookmarks
     * whose destination cannot be resolved (orphan named refs, non-GoTo actions, missing
     * destinations) are emitted with {@code @?} as the page marker so they remain visible to the
     * editor; the parser rejects {@code @?} on apply, forcing an explicit decision.
     */
    public static String dumpToString(PdfDocument pdfDoc) {
        PdfOutline root = pdfDoc.getOutlines(false);
        if (root == null) return "";

        IPdfNameTreeAccess destNames = pdfDoc.getCatalog().getNameTree(PdfName.Dests);
        StringBuilder sb = new StringBuilder();
        for (PdfOutline child : root.getAllChildren()) {
            renderOutline(child, 0, pdfDoc, destNames, sb);
        }
        return sb.toString();
    }

    private static void renderOutline(
            PdfOutline outline,
            int depth,
            PdfDocument doc,
            IPdfNameTreeAccess destNames,
            StringBuilder sb) {
        Integer page = pageOf(outline, doc, destNames);
        sb.append(" ".repeat(depth * INDENT_SPACES));
        sb.append(sanitizeTitle(outline.getTitle()));
        sb.append("  @").append(page == null ? "?" : page).append('\n');
        for (PdfOutline child : outline.getAllChildren()) {
            renderOutline(child, depth + 1, doc, destNames, sb);
        }
    }

    /**
     * Resolves an outline's destination to a 1-based page number, following named destinations
     * through {@code destNames}. iText's {@link
     * com.itextpdf.kernel.pdf.navigation.PdfDestination#getDestinationPage(IPdfNameTreeAccess)}
     * accepts both explicit array destinations and named ones (PdfString/PdfName), returning either
     * a page dictionary or a {@code PdfNumber} index into the document. Returns null if the
     * destination is absent, unresolvable, or points outside the document.
     */
    private static Integer pageOf(
            PdfOutline outline, PdfDocument doc, IPdfNameTreeAccess destNames) {
        if (outline.getDestination() == null) return null;
        PdfObject pageObj = outline.getDestination().getDestinationPage(destNames);
        if (pageObj instanceof PdfDictionary pageDict) {
            int n = doc.getPageNumber(pageDict);
            return n > 0 ? n : null;
        }
        if (pageObj instanceof PdfNumber pn) {
            // Remote/embedded destinations use a 0-based page index; clamp to in-document range.
            int n = pn.intValue() + 1;
            return (n >= 1 && n <= doc.getNumberOfPages()) ? n : null;
        }
        return null;
    }

    /**
     * Strips characters that would corrupt the line-oriented format: control chars, embedded
     * newlines, and trailing whitespace are dropped. Titles that legitimately contain {@code @N}
     * survive because the page sigil only matches at end of line.
     */
    static String sanitizeTitle(String title) {
        if (title == null) return "";
        String cleaned = title.replaceAll("[\\x00-\\x1F]", " ").replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    // === Apply ================================================================

    /** Result of applying an edited outline file. */
    public record ApplyResult(int previousEntries, int newEntries, int parseErrors) {}

    /**
     * Parses {@code content} and replaces the document's outline with the parsed tree. Existing
     * {@code /Outlines} is removed first. Per-line parse errors are reported via {@code warn}; a
     * line with an unrecoverable error (bad indent, missing page sigil) is skipped but parsing
     * continues so the user sees all problems at once.
     *
     * @throws IllegalStateException if the document was not opened for modification
     */
    public static ApplyResult applyFromString(
            PdfDocument pdfDoc, String content, Consumer<String> warn) {
        if (pdfDoc.getWriter() == null) {
            throw new IllegalStateException("PDF must be opened for modification");
        }
        ParseResult parsed = parse(content, warn);

        int previousEntries = countEntries(pdfDoc.getOutlines(false));
        pdfDoc.getCatalog().getPdfObject().remove(PdfName.Outlines);
        PdfOutline root = pdfDoc.getOutlines(true);

        int built = 0;
        for (Node n : parsed.roots()) {
            built += buildOutline(pdfDoc, root, n, warn);
        }
        return new ApplyResult(previousEntries, built, parsed.errors());
    }

    private static int countEntries(PdfOutline outline) {
        if (outline == null) return 0;
        int n = 0;
        for (PdfOutline child : outline.getAllChildren()) {
            n += 1 + countEntries(child);
        }
        return n;
    }

    private static int buildOutline(
            PdfDocument doc, PdfOutline parent, Node node, Consumer<String> warn) {
        if (node.page() < 1 || node.page() > doc.getNumberOfPages()) {
            warn.accept(
                    "page "
                            + node.page()
                            + " out of range [1, "
                            + doc.getNumberOfPages()
                            + "] for \""
                            + node.title()
                            + "\"; skipping entry and its children");
            return 0;
        }
        PdfOutline created = parent.addOutline(node.title());
        PdfPage page = doc.getPage(node.page());
        // WARNING: Hardcoded to "Inherit Zoom"
        // "A null value for any of the parameters left, top, or zoom specifies that the current
        // value of that parameter shall be retained unchanged. A zoom value of 0 has the same
        // meaning as a null value." (PDF 32000-1:2008 §12.3.2.2)
        created.addDestination(PdfExplicitDestination.createXYZ(page, NaN, NaN, NaN));
        int count = 1;
        for (Node child : node.children()) {
            count += buildOutline(doc, created, child, warn);
        }
        return count;
    }

    // === Parse ================================================================

    /** A single bookmark node parsed from the text format. */
    record Node(String title, int page, List<Node> children) {}

    record ParseResult(List<Node> roots, int errors) {}

    /**
     * Parses the indented text format into a tree of {@link Node}s. The grammar:
     *
     * <ul>
     *   <li>Lines beginning with {@code #} (after optional whitespace) are comments.
     *   <li>Blank lines are ignored.
     *   <li>Indentation is in spaces; tabs are rejected. Each level of nesting is exactly {@value
     *       #INDENT_SPACES} spaces.
     *   <li>Each non-comment, non-blank line ends with {@code @<digits>} or {@code @?} (one or more
     *       spaces, then the page sigil). The portion before that is the title. {@code @?} is the
     *       dump-time marker for an unresolvable destination and is rejected on apply.
     *   <li>A child is at exactly one indent level deeper than its parent; jumping by more than one
     *       level is an error.
     * </ul>
     */
    static ParseResult parse(String content, Consumer<String> warn) {
        List<Node> roots = new ArrayList<>();
        Deque<NodeBuilder> stack = new ArrayDeque<>(); // builders at each open depth
        int errors = 0;
        int lineNum = 0;

        for (String raw : content.split("\n", -1)) {
            lineNum++;
            String trimmed = raw.stripTrailing();
            if (trimmed.isEmpty()) continue;

            int indent = leadingSpaces(trimmed);
            if (indent < 0) {
                warn.accept("line " + lineNum + ": tabs are not allowed for indentation");
                errors++;
                continue;
            }
            String body = trimmed.substring(indent);
            if (body.startsWith("#")) continue;

            if (indent % INDENT_SPACES != 0) {
                warn.accept(
                        "line "
                                + lineNum
                                + ": indent of "
                                + indent
                                + " spaces is not a multiple of "
                                + INDENT_SPACES);
                errors++;
                continue;
            }
            int depth = indent / INDENT_SPACES;
            if (depth > stack.size()) {
                warn.accept(
                        "line "
                                + lineNum
                                + ": indented to depth "
                                + depth
                                + " but no parent at depth "
                                + (depth - 1));
                errors++;
                continue;
            }

            Matcher m = PAGE_SIGIL.matcher(body);
            if (!m.matches()) {
                warn.accept(
                        "line "
                                + lineNum
                                + ": missing page sigil (expected \" @<number>\" or \" @?\" at end)");
                errors++;
                continue;
            }
            String title = m.group(1).stripTrailing();
            String pageToken = m.group(2);
            if ("?".equals(pageToken)) {
                warn.accept(
                        "line "
                                + lineNum
                                + ": unresolved destination (\"@?\"); assign a page number or"
                                + " delete this line");
                errors++;
                continue;
            }
            int page = Integer.parseInt(pageToken);
            if (page < 1) {
                warn.accept("line " + lineNum + ": page number must be >= 1");
                errors++;
                continue;
            }

            // Pop until stack depth matches this node's parent depth.
            while (stack.size() > depth) stack.pop();
            NodeBuilder nb = new NodeBuilder(title, page);
            if (stack.isEmpty()) {
                roots.add(nb.toNode());
                stack.push(nb.withSink(roots.get(roots.size() - 1).children()));
            } else {
                List<Node> siblings = stack.peek().sink();
                siblings.add(nb.toNode());
                stack.push(nb.withSink(siblings.get(siblings.size() - 1).children()));
            }
        }
        return new ParseResult(roots, errors);
    }

    /** Returns the count of leading spaces, or -1 if a tab appears in the leading whitespace. */
    private static int leadingSpaces(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ') i++;
            else if (c == '\t') return -1;
            else break;
        }
        return i;
    }

    /**
     * Tracks a node under construction and the {@code children} list of its already-emitted Node.
     * Used as a stack frame so deeper lines know where to append.
     */
    private static final class NodeBuilder {
        final String title;
        final int page;
        List<Node> sink;

        NodeBuilder(String title, int page) {
            this.title = title;
            this.page = page;
        }

        Node toNode() {
            return new Node(title, page, new ArrayList<>());
        }

        NodeBuilder withSink(List<Node> sink) {
            this.sink = sink;
            return this;
        }

        List<Node> sink() {
            return sink;
        }
    }
}
