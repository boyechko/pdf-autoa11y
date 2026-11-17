// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructNode;
import net.boyechko.pdf.autoa11y.document.Tag;

/**
 * {@code !REORDER_KIDS}: reorders the element's kids based on each kid's {@code REORDER NNN}
 * scribble segment. The kid's marker carries no {@code !}, since it is data read by this
 * instruction rather than an instruction of its own. Kids with no {@code REORDER NNN} are appended
 * at the end in their original relative order. After reordering, the {@code REORDER NNN} segments
 * on kids and the {@code !REORDER_KIDS} segment on the parent are cleared (identity scribbles, if
 * any, are preserved).
 */
record ReorderKidsInstruction() implements ScribbledInstruction {

    /** Head of the position marker segment on each kid. */
    private static final String POSITION_MARKER = "REORDER";

    /** Pattern for extracting a 1-based position from a kid's {@code REORDER NNN} segment. */
    private static final Pattern REORDER_POSITION_PATTERN =
            Pattern.compile(POSITION_MARKER + "\\s+(\\d+)");

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        Tag parent = Tag.of(element);
        List<StructNode> all = parent.kids();
        if (all.isEmpty()) {
            parent.unstamp("!REORDER_KIDS");
            return null;
        }

        List<AnnotatedKid> annotated = new ArrayList<>();
        List<StructNode> unannotated = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            StructNode kid = all.get(i);
            Integer pos = kid instanceof Tag tag ? reorderPositionOf(tag) : null;
            if (pos != null) annotated.add(new AnnotatedKid((Tag) kid, pos, i + 1));
            else unannotated.add(kid);
        }
        annotated.sort(Comparator.comparingInt(AnnotatedKid::position));

        // Annotated kids in sorted order, then the rest in their original order.
        List<StructNode> order = new ArrayList<>();
        annotated.forEach(a -> order.add(a.kid()));
        order.addAll(unannotated);
        parent.reorderKids(order);

        // Clear the consumed instruction and leave a receipt naming the move. Receipts left by an
        // earlier run are swept tree-wide by ScribbledInstructionCheck before this one starts.
        for (int newIdx = 0; newIdx < annotated.size(); newIdx++) {
            AnnotatedKid a = annotated.get(newIdx);
            a.kid().unstamp(POSITION_MARKER);
            int oldPos = a.originalIndex();
            int newPos = newIdx + 1;
            if (oldPos != newPos) {
                a.kid().stamp(receipt("REORDER " + oldPos + " -> " + newPos));
            }
        }
        parent.unstamp("!REORDER_KIDS");
        return null;
    }

    private record AnnotatedKid(Tag kid, int position, int originalIndex) {}

    /** Extracts the {@code NNN} from a kid's {@code REORDER NNN} scribble segment, or null. */
    private static Integer reorderPositionOf(Tag kid) {
        for (String segment : kid.scribble().segments()) {
            Matcher m = REORDER_POSITION_PATTERN.matcher(segment);
            if (m.matches()) return Integer.parseInt(m.group(1));
        }
        return null;
    }
}
