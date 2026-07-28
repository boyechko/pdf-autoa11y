// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Annotation;
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

    /**
     * Scribble segment head identifying this fix's own /T output (e.g. "INST OK"). Segments with
     * this head are cleared before each run so completion markers don't accumulate.
     */
    public static final String INSTRUCTION_TAG = "INST";

    /** Full completion scribble ("INST OK") an applyXxx returns for the dispatcher to stamp. */
    private static final String OK_SCRIBBLE = INSTRUCTION_TAG + " OK";

    private static final Pattern ADD_CHILD_PATTERN = Pattern.compile("!ADD_CHILD(?:REN)?\\s+(.+)");
    private static final Pattern ADD_PARENT_PATTERN = Pattern.compile("!ADD_PARENTS?\\s+(.+)");
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("!ARTIFACT");
    private static final Pattern REORDER_KIDS_PATTERN = Pattern.compile("!REORDER_KIDS");
    private static final Pattern SET_ROLE_PATTERN = Pattern.compile("!SET_ROLE\\s+(\\S+)");
    private static final Pattern SPLIT_LINES_PATTERN =
            Pattern.compile("!SPLIT_LINES(?:\\s+([\\d,]+))?");
    private static final Pattern UNLINK_PATTERN = Pattern.compile("!UNLINK");
    private static final Pattern UNWRAP_LIST_PATTERN = Pattern.compile("!UNWRAP_LIST");

    /** Pattern for extracting a 1-based position from a kid's {@code !REORDER NNN} segment. */
    private static final Pattern REORDER_POSITION_PATTERN = Pattern.compile("!REORDER\\s+(\\d+)");

    private final String instruction;
    private final PdfStructElem element;

    public ScribbledInstructionFix(PdfStructElem element, String instruction) {
        this.element = element;
        this.instruction = instruction;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        Matcher addChild = ADD_CHILD_PATTERN.matcher(instruction);
        Matcher addParent = ADD_PARENT_PATTERN.matcher(instruction);
        Matcher artifact = ARTIFACT_PATTERN.matcher(instruction);
        Matcher reorderKids = REORDER_KIDS_PATTERN.matcher(instruction);
        Matcher setRole = SET_ROLE_PATTERN.matcher(instruction);
        Matcher splitLines = SPLIT_LINES_PATTERN.matcher(instruction);
        Matcher unlink = UNLINK_PATTERN.matcher(instruction);
        Matcher unwrapList = UNWRAP_LIST_PATTERN.matcher(instruction);

        // Each applyXxx returns the scribble to stamp onto /T ("INST OK", or "INST OK (was: P)"
        // for a role change), or null to leave /T untouched because the instruction manages its
        // own scribble or destroys the element.
        String okScribble;
        if (addChild.matches()) {
            okScribble = applyAddChild(ctx, addChild.group(1));
        } else if (addParent.matches()) {
            okScribble = applyAddParent(ctx, addParent.group(1));
        } else if (artifact.matches()) {
            okScribble = applyArtifact(ctx);
        } else if (reorderKids.matches()) {
            okScribble = applyReorderKids(ctx);
        } else if (setRole.matches()) {
            okScribble = applySetRole(setRole.group(1));
        } else if (splitLines.matches()) {
            okScribble = applySplitLines(ctx, splitLines.group(1));
        } else if (unlink.matches()) {
            okScribble = applyUnlink(ctx);
        } else if (unwrapList.matches()) {
            okScribble = applyUnwrapList();
        } else {
            throw new IllegalArgumentException("Unsupported instruction: " + instruction);
        }

        // setToolScribble replaces /T wholesale, so the scribbled instruction is cleared here.
        if (okScribble != null) {
            StructTree.setToolScribble(element, okScribble);
        }
    }

    // === Instruction: ADD_CHILD ==============================================

    /**
     * Parses the tag expression and redistributes the element's kids into the new wrappers per the
     * template. Enforces strict coverage: every existing kid must land in some range. On an empty
     * element, coverage is trivially satisfied and only new empty wrappers are created.
     */
    private String applyAddChild(DocContext ctx, String tagExpr) {
        List<ChildSpec> specs = parseTemplate(tagExpr);

        // Snapshot original kids as IStructureNode wrappers (so removeKid/addKid calls can
        // update the document's ParentTree alongside the K array).
        List<IStructureNode> origKids =
                element.getKids() == null ? List.of() : new ArrayList<>(element.getKids());
        int kidCount = origKids.size();
        validateCoverage(specs, kidCount, tagExpr);

        PdfObject effectivePg = StructTree.effectivePageDict(element);
        for (ChildSpec spec : specs) {
            PdfStructElem wrapper = new PdfStructElem(ctx.doc(), resolvePdfName(spec.tag()));
            element.addKid(wrapper);
            populateWrapper(ctx.doc(), wrapper, spec, origKids, kidCount, effectivePg);
        }
        return OK_SCRIBBLE;
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
                PdfStructElem nested = new PdfStructElem(doc, resolvePdfName(child.tag()));
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

    // === Instruction: SET_ROLE ===============================================

    private String applySetRole(String roleName) {
        String prevRole = element.getRole().getValue();
        element.setRole(resolvePdfName(roleName));
        return OK_SCRIBBLE + " (was: " + prevRole + ")";
    }

    /** Resolves a PDF name string to a standard {@link PdfName} constant when one exists. */
    private static PdfName resolvePdfName(String name) {
        try {
            return (PdfName) PdfName.class.getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            return new PdfName(name);
        }
    }

    // === Instruction: ARTIFACT ===============================================

    /**
     * Delegates to MistaggedArtifactFix to convert the element's content to artifacts. Returns null
     * because the element is removed from the tree, so there is nothing left to mark "INST OK".
     */
    private String applyArtifact(DocContext ctx) throws Exception {
        new MistaggedArtifactFix(element).apply(ctx);
        return null;
    }

    // === Instruction: SPLIT_LINES ============================================

    /** Delegates to SplitIntoListItemsFix to split the element's lumped blocks into list items. */
    private String applySplitLines(DocContext ctx, String spec) throws Exception {
        SplitIntoListItemsFix fix = new SplitIntoListItemsFix(element, spec);
        fix.apply(ctx);
        StructTree.addScribble(fix.resultingList(), OK_SCRIBBLE + " (on child)");
        return OK_SCRIBBLE;
    }

    /** Returns true if the instruction operates on the entire subtree, not just the element. */
    public static boolean isSubtreeInstruction(String instruction) {
        return ARTIFACT_PATTERN.matcher(instruction).matches();
    }

    // === Instruction: REORDER ================================================

    /**
     * Reorders the element's kids based on each kid's {@code !REORDER NNN} scribble segment. Kids
     * with no {@code !REORDER NNN} are appended at the end in their original relative order. After
     * reordering, the {@code !REORDER NNN} segments on kids and the {@code !REORDER_KIDS} segment
     * on the parent are cleared (identity scribbles, if any, are preserved).
     */
    private String applyReorderKids(DocContext ctx) {
        List<IStructureNode> all =
                element.getKids() == null ? List.of() : new ArrayList<>(element.getKids());
        if (all.isEmpty()) {
            StructTree.clearScribbleSegments(element, "!REORDER_KIDS");
            return null;
        }

        List<AnnotatedKid> annotated = new ArrayList<>();
        List<IStructureNode> unannotated = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            IStructureNode kid = all.get(i);
            Integer pos = reorderPositionOf(kid);
            if (pos != null) annotated.add(new AnnotatedKid(kid, pos, i + 1));
            else unannotated.add(kid);
        }
        annotated.sort(Comparator.comparingInt(AnnotatedKid::position));

        // Detach all, then re-attach in the new order. Sequential addKid appends, so the final
        // K-array ends up: annotated kids in sorted order, then unannotated in their original
        // order.
        for (IStructureNode kid : all) element.removeKid(kid);
        for (AnnotatedKid a : annotated) addToParent(element, a.kid());
        for (IStructureNode kid : unannotated) addToParent(element, kid);

        // Clear consumed instructions and record the move as a "MOVE old → new" breadcrumb.
        for (int newIdx = 0; newIdx < annotated.size(); newIdx++) {
            AnnotatedKid a = annotated.get(newIdx);
            if (a.kid() instanceof PdfStructElem se) {
                StructTree.clearScribbleSegments(se, "!REORDER");
                StructTree.clearScribbleSegments(se, "MOVE");
                int oldPos = a.originalIndex();
                int newPos = newIdx + 1;
                if (oldPos != newPos) {
                    StructTree.addScribble(se, "MOVE " + oldPos + " -> " + newPos);
                }
            }
        }
        StructTree.clearScribbleSegments(element, "!REORDER_KIDS");
        return null;
    }

    private record AnnotatedKid(IStructureNode kid, int position, int originalIndex) {}

    /** Extracts the {@code NNN} from a kid's {@code !REORDER NNN} scribble segment, or null. */
    private static Integer reorderPositionOf(IStructureNode kid) {
        if (!(kid instanceof PdfStructElem se)) return null;
        var scribble = StructTree.getScribble(se);
        if (scribble == null) return null;
        Matcher m = REORDER_POSITION_PATTERN.matcher(scribble.value());
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** Type-switch dispatcher for addKid (which is typed on PdfStructElem vs. PdfMcr). */
    private static void addToParent(PdfStructElem parent, IStructureNode kid) {
        switch (kid) {
            case PdfStructElem se -> parent.addKid(se);
            case PdfMcr mcr -> parent.addKid(mcr);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported kid type: " + kid.getClass().getSimpleName());
        }
    }

    // === Instruction: UNLINK =================================================

    /**
     * Unwraps a Link struct element: promotes its non-OBJR kids to its parent at its original
     * position, removes the Link element, and deletes the associated Link annotation from its
     * page's /Annots array. The element is destroyed, so no breadcrumb is written.
     */
    private String applyUnlink(DocContext ctx) {
        if (!PdfName.Link.equals(element.getRole())) {
            throw new IllegalArgumentException(
                    "!UNLINK requires a Link element, got: " + element.getRole());
        }

        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot unlink: element has no parent");
            return null;
        }
        int origIdx = StructTree.findKidIndex(parent, element);
        if (origIdx < 0) {
            logger.warn("Cannot unlink: element not found in parent's kids");
            return null;
        }

        List<IStructureNode> origKids =
                element.getKids() == null ? List.of() : new ArrayList<>(element.getKids());

        PdfDictionary annotDict = null;
        for (IStructureNode kid : origKids) {
            if (kid instanceof PdfObjRef objRef) {
                PdfObject refObj = objRef.getReferencedObject();
                if (refObj instanceof PdfDictionary dict) {
                    annotDict = dict;
                    break;
                }
            }
        }

        PdfObject linkEffectivePg = StructTree.effectivePageDict(element);

        int insertAt = origIdx;
        for (IStructureNode kid : origKids) {
            if (kid instanceof PdfObjRef) {
                continue;
            }
            if (kid instanceof PdfStructElem childElem) {
                element.removeKid(childElem);
                parent.addKid(insertAt++, childElem);
            } else if (kid instanceof PdfMcr mcr) {
                PdfObject underlying = mcr.getPdfObject();
                element.removeKid(mcr);
                PdfMcr rebound;
                if (underlying instanceof PdfNumber num) {
                    // Bare-int MCRs resolve their page via ancestor /Pg. If the parent lacks
                    // an explicit /Pg, set it now so the promoted MCR can still find its page.
                    if (linkEffectivePg != null && parent.getPdfObject().get(PdfName.Pg) == null) {
                        parent.getPdfObject().put(PdfName.Pg, linkEffectivePg);
                        parent.setModified();
                    }
                    rebound = new PdfMcrNumber(num, parent);
                } else {
                    rebound = new PdfMcrDictionary((PdfDictionary) underlying, parent);
                }
                parent.addKid(insertAt++, rebound);
            }
        }

        parent.removeKid(element);

        if (annotDict != null && !Annotation.removeFromAnyPage(ctx.doc(), annotDict)) {
            int objNum =
                    annotDict.getIndirectReference() != null
                            ? annotDict.getIndirectReference().getObjNumber()
                            : 0;
            logger.debug("Link annotation {} not found in any page /Annots", Format.objNum(objNum));
        }
        return null;
    }

    // === Instruction: UNWRAP_LIST ============================================

    /**
     * Undoes a bare list conversion: hoists each LI &gt; LBody's wrapped elements back to the L's
     * parent at the L's position, then removes the L and its wrappers. Only lists whose every item
     * is an Lbl-less LI &gt; LBody chain wrapping structure elements qualify; anything else (real
     * bullets, direct MCR content) is refused before any mutation, leaving the tree untouched. The
     * element is destroyed, so no breadcrumb is written.
     */
    private String applyUnwrapList() {
        String role = StructTree.mappedRole(element);
        if (!"L".equals(role)) {
            throw new IllegalArgumentException("!UNWRAP_LIST requires an L element, got: " + role);
        }
        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot unwrap list: element has no parent");
            return null;
        }
        int insertAt = StructTree.findKidIndex(parent, element);
        if (insertAt < 0) {
            logger.warn("Cannot unwrap list: element not found in parent's kids");
            return null;
        }

        // Validate the whole list before touching anything, so a refusal is side-effect free.
        List<PdfStructElem> hoistees = new ArrayList<>();
        for (IStructureNode kid : kidsOf(element)) {
            PdfStructElem li = requireRole(kid, "LI", "list item");
            for (IStructureNode liKid : kidsOf(li)) {
                PdfStructElem lBody = requireRole(liKid, "LBody", "LI kid");
                for (IStructureNode bodyKid : kidsOf(lBody)) {
                    if (!(bodyKid instanceof PdfStructElem wrapped)) {
                        throw new IllegalArgumentException(
                                "!UNWRAP_LIST refuses: LBody holds direct content, not a wrapped"
                                        + " element");
                    }
                    hoistees.add(wrapped);
                }
            }
        }

        for (PdfStructElem wrapped : hoistees) {
            // Pin the page the element resolved through its old ancestors, so bare-int MCRs
            // inside it still find their page under the new parent.
            PdfObject effectivePg = StructTree.effectivePageDict(wrapped);
            if (effectivePg != null && wrapped.getPdfObject().get(PdfName.Pg) == null) {
                wrapped.getPdfObject().put(PdfName.Pg, effectivePg);
                wrapped.setModified();
            }
            ((PdfStructElem) wrapped.getParent()).removeKid(wrapped);
            parent.addKid(insertAt++, wrapped);
        }
        parent.removeKid(element);
        return null;
    }

    /** Returns the node's kids, or an empty list when it has none. */
    private static List<IStructureNode> kidsOf(PdfStructElem elem) {
        return elem.getKids() == null ? List.of() : new ArrayList<>(elem.getKids());
    }

    /** Asserts the kid is a struct elem with the given mapped role, or refuses the unwrap. */
    private static PdfStructElem requireRole(IStructureNode kid, String role, String what) {
        if (kid instanceof PdfStructElem se && role.equals(StructTree.mappedRole(se))) {
            return se;
        }
        String got =
                kid instanceof PdfStructElem se
                        ? StructTree.mappedRole(se)
                        : kid.getClass().getSimpleName();
        throw new IllegalArgumentException(
                "!UNWRAP_LIST refuses: expected " + what + " to be " + role + ", got " + got);
    }

    // === Instruction: ADD_PARENT =============================================

    /**
     * Parses the tag expression, which must be a linear chain (each node has at most one child and
     * the innermost is a leaf), and wraps the element in that chain. For example, {@code
     * Reference[Link[P[]]]} applied to a Span produces {@code Reference[Link[P[Span]]]}.
     */
    private String applyAddParent(DocContext ctx, String tagExpr) {
        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot add parent: element has no parent");
            return null;
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
            PdfStructElem wrapper = new PdfStructElem(ctx.doc(), resolvePdfName(wrapperName));
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
        return OK_SCRIBBLE;
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
