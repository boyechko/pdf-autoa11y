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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.StructTree.Node;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Carries out the instruction scribbled in the structure element's /T key. */
public class ScribbledInstructionFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ScribbledInstructionFix.class);

    /** Tag written to /T after a successful fix (as "INST OK") and cleared on each run. */
    public static final String CHECK_SCRIBBLE_PREFIX = "INST";

    private static final Pattern ADD_CHILD_PATTERN = Pattern.compile("!ADD_CHILD(?:REN)?\\s+(.+)");
    private static final Pattern ADD_PARENT_PATTERN = Pattern.compile("!ADD_PARENTS?\\s+(.+)");
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("!ARTIFACT");

    private final String instruction;
    private final PdfStructElem element;

    public ScribbledInstructionFix(PdfStructElem element, String instruction) {
        this.element = element;
        this.instruction = instruction;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        Matcher addChild = ADD_CHILD_PATTERN.matcher(instruction);
        Matcher addParent = ADD_PARENT_PATTERN.matcher(instruction);
        Matcher artifact = ARTIFACT_PATTERN.matcher(instruction);

        if (addChild.matches()) {
            applyAddChild(ctx, addChild.group(1));
        } else if (addParent.matches()) {
            applyAddParent(ctx, addParent.group(1));
        } else if (artifact.matches()) {
            applyArtifact(ctx);
        } else {
            throw new IllegalArgumentException("Unsupported instruction: " + instruction);
        }
    }

    /**
     * Parses the tag expression and redistributes the element's kids into the new wrappers per the
     * template. Enforces strict coverage: every existing kid must land in some range. On an empty
     * element, coverage is trivially satisfied and only new empty wrappers are created.
     */
    private void applyAddChild(DocContext ctx, String tagExpr) {
        List<ChildSpec> specs = parseTemplate(tagExpr);

        // Snapshot original kids as IStructureNode wrappers (so removeKid/addKid calls can
        // update the document's ParentTree alongside the K array).
        List<IStructureNode> origKids =
                element.getKids() == null ? List.of() : new ArrayList<>(element.getKids());
        int kidCount = origKids.size();
        validateCoverage(specs, kidCount, tagExpr);

        PdfObject effectivePg = StructTree.effectivePageDict(element);
        for (ChildSpec spec : specs) {
            PdfStructElem wrapper = new PdfStructElem(ctx.doc(), new PdfName(spec.tag()));
            element.addKid(wrapper);
            populateWrapper(ctx.doc(), wrapper, spec, origKids, kidCount, effectivePg);
        }

        element.getPdfObject().remove(PdfName.T);
        StructTree.setScribble(element, CHECK_SCRIBBLE_PREFIX + " OK");
    }

    private void populateWrapper(
            PdfDocument doc,
            PdfStructElem wrapper,
            ChildSpec spec,
            List<IStructureNode> origKids,
            int kidCount,
            PdfObject effectivePg) {
        if (spec instanceof NewStructure ns) {
            for (ChildSpec child : ns.children()) {
                PdfStructElem nested = new PdfStructElem(doc, new PdfName(child.tag()));
                wrapper.addKid(nested);
                populateWrapper(doc, nested, child, origKids, kidCount, effectivePg);
            }
        } else if (spec instanceof WrapRange wr) {
            // Set /Pg on the immediate parent of MCRs so Acrobat preflight accepts them.
            if (effectivePg != null) {
                wrapper.getPdfObject().put(PdfName.Pg, effectivePg);
            }
            int from = wr.from() - 1;
            int to = Math.min(wr.to(), kidCount);
            for (int i = from; i < to; i++) {
                StructTree.moveKid(origKids.get(i), element, wrapper);
            }
        }
    }

    /** Validates that ranges cover 1..kidCount exactly, appearing in ascending order. */
    private static void validateCoverage(List<ChildSpec> specs, int kidCount, String tagExpr) {
        List<WrapRange> ranges = new ArrayList<>();
        collectRanges(specs, ranges);
        if (ranges.isEmpty()) return;

        int expectedNext = 1;
        for (WrapRange wr : ranges) {
            if (wr.from() != expectedNext) {
                throw new IllegalArgumentException(
                        String.format(
                                "!ADD_CHILDREN ranges must be ascending and contiguous; expected"
                                        + " index %d, got %d in: %s",
                                expectedNext, wr.from(), tagExpr));
            }
            int to = Math.min(wr.to(), kidCount);
            if (to < wr.from()) {
                throw new IllegalArgumentException("!ADD_CHILDREN range is empty in: " + tagExpr);
            }
            expectedNext = to + 1;
        }
        if (expectedNext - 1 != kidCount) {
            throw new IllegalArgumentException(
                    String.format(
                            "!ADD_CHILDREN ranges must cover all %d kid(s); last covered index"
                                    + " was %d in: %s",
                            kidCount, expectedNext - 1, tagExpr));
        }
    }

    /** Collects all WrapRange specs in depth-first order. */
    private static void collectRanges(List<ChildSpec> specs, List<WrapRange> out) {
        for (ChildSpec spec : specs) {
            if (spec instanceof WrapRange wr) {
                out.add(wr);
            } else if (spec instanceof NewStructure ns) {
                collectRanges(ns.children(), out);
            }
        }
    }

    // --- Template parser --------------------------------------------------

    /** Template AST for !ADD_CHILDREN. Top-level list may mix both variants. */
    private sealed interface ChildSpec {
        String tag();
    }

    /** A new empty wrapper (possibly containing further nested empty wrappers). */
    private record NewStructure(String tag, List<ChildSpec> children) implements ChildSpec {}

    /** A wrapper that claims existing kids in the 1-based inclusive range [from, to]. */
    private record WrapRange(String tag, int from, int to) implements ChildSpec {
        /** Sentinel: open-ended range (N..) to the last kid. */
        static final int OPEN_END = Integer.MAX_VALUE;
    }

    /** Parses an !ADD_CHILDREN template into a list of top-level ChildSpecs. */
    static List<ChildSpec> parseTemplate(String expr) {
        int[] pos = {0};
        List<ChildSpec> out = parseSpecList(expr, pos);
        skipWhitespaceAndCommas(expr, pos);
        if (pos[0] < expr.length()) {
            throw new IllegalArgumentException(
                    "Unexpected trailing content at position " + pos[0] + " in: " + expr);
        }
        return out;
    }

    private static List<ChildSpec> parseSpecList(String expr, int[] pos) {
        List<ChildSpec> list = new ArrayList<>();
        while (pos[0] < expr.length()) {
            skipWhitespaceAndCommas(expr, pos);
            if (pos[0] >= expr.length() || expr.charAt(pos[0]) == ']') {
                break;
            }
            list.add(parseSpec(expr, pos));
        }
        return list;
    }

    private static ChildSpec parseSpec(String expr, int[] pos) {
        int nameStart = pos[0];
        while (pos[0] < expr.length()
                && expr.charAt(pos[0]) != '['
                && expr.charAt(pos[0]) != ']'
                && expr.charAt(pos[0]) != ',') {
            pos[0]++;
        }
        String tag = expr.substring(nameStart, pos[0]).trim();
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Missing tag name at position " + pos[0]);
        }

        // A wrapper without [...] body is a leaf new structure.
        if (pos[0] >= expr.length() || expr.charAt(pos[0]) != '[') {
            return new NewStructure(tag, List.of());
        }
        pos[0]++; // skip '['

        // Peek first non-space char to decide: digit → range; otherwise → nested template.
        int save = pos[0];
        while (save < expr.length() && expr.charAt(save) == ' ') save++;
        char peek = save < expr.length() ? expr.charAt(save) : ']';

        ChildSpec result;
        if (Character.isDigit(peek)) {
            result = parseRangeWrapper(tag, expr, pos);
        } else {
            List<ChildSpec> children = parseSpecList(expr, pos);
            result = new NewStructure(tag, children);
        }

        if (pos[0] < expr.length() && expr.charAt(pos[0]) == ']') {
            pos[0]++;
        } else {
            throw new IllegalArgumentException("Missing ']' in: " + expr);
        }
        return result;
    }

    private static ChildSpec parseRangeWrapper(String tag, String expr, int[] pos) {
        skipSpaces(expr, pos);
        int from = parseInt(expr, pos);
        int to = from;
        skipSpaces(expr, pos);
        if (pos[0] + 1 < expr.length()
                && expr.charAt(pos[0]) == '.'
                && expr.charAt(pos[0] + 1) == '.') {
            pos[0] += 2;
            skipSpaces(expr, pos);
            if (pos[0] < expr.length() && Character.isDigit(expr.charAt(pos[0]))) {
                to = parseInt(expr, pos);
                if (to < from) {
                    throw new IllegalArgumentException(
                            "Range end " + to + " precedes start " + from + " in: " + expr);
                }
            } else {
                to = WrapRange.OPEN_END;
            }
        }
        skipSpaces(expr, pos);
        if (from < 1) {
            throw new IllegalArgumentException(
                    "Range indices are 1-based; got " + from + " in: " + expr);
        }
        return new WrapRange(tag, from, to);
    }

    private static int parseInt(String expr, int[] pos) {
        int start = pos[0];
        while (pos[0] < expr.length() && Character.isDigit(expr.charAt(pos[0]))) {
            pos[0]++;
        }
        return Integer.parseInt(expr.substring(start, pos[0]));
    }

    private static void skipSpaces(String expr, int[] pos) {
        while (pos[0] < expr.length() && expr.charAt(pos[0]) == ' ') pos[0]++;
    }

    private static void skipWhitespaceAndCommas(String expr, int[] pos) {
        while (pos[0] < expr.length()
                && (expr.charAt(pos[0]) == ' ' || expr.charAt(pos[0]) == ',')) {
            pos[0]++;
        }
    }

    /** Delegates to MistaggedArtifactFix to convert the element's content to artifacts. */
    private void applyArtifact(DocContext ctx) throws Exception {
        new MistaggedArtifactFix(element).apply(ctx);
    }

    /**
     * Parses the tag expression, which must be a linear chain (each node has at most one child and
     * the innermost is a leaf), and wraps the element in that chain. For example, {@code
     * Reference[Link[P[]]]} applied to a Span produces {@code Reference[Link[P[Span]]]}.
     */
    private void applyAddParent(DocContext ctx, String tagExpr) {
        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot add parent: element has no parent");
            return;
        }

        List<Node<String>> nodes = Node.fromString(tagExpr);
        if (nodes.size() != 1) {
            throw new IllegalArgumentException(
                    "!ADD_PARENT requires exactly one wrapper chain root, got: " + tagExpr);
        }

        List<String> chain = linearChain(nodes.get(0), tagExpr);

        int index = StructTree.findKidIndex(parent, element);

        // Build the chain top-down: each wrapper must have /P set (via addKid on its own
        // parent) before we can addKid into it.
        PdfStructElem innermost = parent;
        PdfStructElem outermost = null;
        for (String wrapperName : chain) {
            PdfStructElem wrapper = new PdfStructElem(ctx.doc(), new PdfName(wrapperName));
            if (outermost == null) {
                parent.addKid(index, wrapper);
                outermost = wrapper;
            } else {
                innermost.addKid(wrapper);
            }
            innermost = wrapper;
        }

        parent.removeKid(element);
        innermost.addKid(element);

        element.getPdfObject().remove(PdfName.T);
        StructTree.setScribble(element, CHECK_SCRIBBLE_PREFIX + " OK");
    }

    /**
     * Flattens a linear chain of single-child nodes into a list of role names, outermost first.
     * Rejects branching (more than one child at any level).
     */
    private static List<String> linearChain(Node<String> root, String tagExpr) {
        List<String> names = new ArrayList<>();
        Node<String> current = root;
        while (true) {
            names.add(current.value());
            List<Node<String>> kids = current.children();
            if (kids.isEmpty()) {
                return names;
            }
            if (kids.size() > 1) {
                throw new IllegalArgumentException(
                        "!ADD_PARENT requires a linear chain (one child per level), got: "
                                + tagExpr);
            }
            current = kids.get(0);
        }
    }

    @Override
    public String describe() {
        return "Carried out scribbled instruction '" + instruction + "'";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "Scribbled instruction fixes";
    }
}
