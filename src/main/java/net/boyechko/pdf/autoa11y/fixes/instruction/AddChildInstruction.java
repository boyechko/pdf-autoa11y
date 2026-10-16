// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * {@code !ADD_CHILD <template>}: parses the tag expression and redistributes the element's kids
 * into the new wrappers per the template. Enforces strict coverage: every existing kid must land in
 * some range. On an empty element, coverage is trivially satisfied and only new empty wrappers are
 * created.
 */
record AddChildInstruction(String tagExpr) implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        List<ChildSpec> specs = parseTemplate(tagExpr);

        // Snapshot original kids as IStructureNode wrappers (so removeKid/addKid calls can
        // update the document's ParentTree alongside the K array).
        List<IStructureNode> origKids = new ArrayList<>(StructTree.kidsOf(element));
        validateCoverage(specs, origKids.size());

        PdfObject effectivePg = StructTree.effectivePageDict(element);
        for (ChildSpec spec : specs) {
            PdfStructElem wrapper = new PdfStructElem(ctx.doc(), RoleMap.toPdfName(spec.tag()));
            element.addKid(wrapper);
            populateWrapper(ctx.doc(), element, wrapper, spec, origKids, effectivePg);
        }
        return receipt("ADD_CHILD " + tagExpr);
    }

    private static void populateWrapper(
            PdfDocument doc,
            PdfStructElem element,
            PdfStructElem wrapper,
            ChildSpec spec,
            List<IStructureNode> origKids,
            PdfObject effectivePg) {
        if (spec instanceof NewStructure ns) {
            for (ChildSpec child : ns.children()) {
                PdfStructElem nested = new PdfStructElem(doc, RoleMap.toPdfName(child.tag()));
                wrapper.addKid(nested);
                populateWrapper(doc, element, nested, child, origKids, effectivePg);
            }
        } else if (spec instanceof WrapRange wr) {
            // Set /Pg on the immediate parent of MCRs so Acrobat preflight accepts them.
            if (effectivePg != null) {
                wrapper.getPdfObject().put(PdfName.Pg, effectivePg);
            }
            int from = wr.from() - 1;
            int to = Math.min(wr.to(), origKids.size());
            for (int i = from; i < to; i++) {
                StructTree.moveKid(origKids.get(i), element, wrapper);
            }
        }
    }

    /** Validates that ranges cover 1..kidCount exactly, appearing in ascending order. */
    private void validateCoverage(List<ChildSpec> specs, int kidCount) {
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
    private static List<ChildSpec> parseTemplate(String expr) {
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
}
