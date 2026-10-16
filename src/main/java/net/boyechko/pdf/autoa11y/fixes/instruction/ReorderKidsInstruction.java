// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * {@code !REORDER_KIDS}: reorders the element's kids based on each kid's {@code !REORDER NNN}
 * scribble segment. Kids with no {@code !REORDER NNN} are appended at the end in their original
 * relative order. After reordering, the {@code !REORDER NNN} segments on kids and the {@code
 * !REORDER_KIDS} segment on the parent are cleared (identity scribbles, if any, are preserved).
 */
record ReorderKidsInstruction() implements ScribbledInstruction {

    /** Pattern for extracting a 1-based position from a kid's {@code !REORDER NNN} segment. */
    private static final Pattern REORDER_POSITION_PATTERN = Pattern.compile("!REORDER\\s+(\\d+)");

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        List<IStructureNode> all = new ArrayList<>(StructTree.kidsOf(element));
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

        // Clear the consumed instruction and leave a receipt naming the move. Receipts left by an
        // earlier run are swept tree-wide by ScribbledInstructionCheck before this one starts.
        for (int newIdx = 0; newIdx < annotated.size(); newIdx++) {
            AnnotatedKid a = annotated.get(newIdx);
            if (a.kid() instanceof PdfStructElem se) {
                StructTree.clearScribbleSegments(se, "!REORDER");
                int oldPos = a.originalIndex();
                int newPos = newIdx + 1;
                if (oldPos != newPos) {
                    StructTree.addToolScribble(se, receipt("REORDER " + oldPos + " -> " + newPos));
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
}
