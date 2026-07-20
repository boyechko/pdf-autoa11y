// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Stamps a list element with a scribble noting its direct item count, e.g. {@code "__:5 items"}.
 * Each list-producing fix calls {@link #update} as its last step, so whichever fix touches a list
 * last leaves the accurate count. An existing count segment is replaced; other segments and the
 * scribble's authorship are preserved.
 */
final class ListItemScribble {

    private static final Pattern COUNT_SEGMENT = Pattern.compile("\\d+ items?");

    private ListItemScribble() {}

    /** Writes or refreshes the item-count segment on a list element. */
    static void update(PdfStructElem list) {
        long count =
                StructTree.childrenOf(list, PdfStructElem.class).stream()
                        .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                        .count();
        String countSegment = count + (count == 1 ? " item" : " items");

        DocValue.Scribble existing = StructTree.getScribble(list);
        if (existing == null) {
            StructTree.setToolScribble(list, countSegment);
            return;
        }

        List<String> kept = new ArrayList<>();
        for (String segment : existing.segments()) {
            if (!COUNT_SEGMENT.matcher(segment.trim()).matches()) {
                kept.add(segment);
            }
        }
        kept.add(countSegment);
        String body = String.join(StructTree.SCRIBBLE_SEPARATOR, kept);
        if (existing.toolAuthored()) {
            StructTree.setToolScribble(list, body);
        } else {
            StructTree.setScribble(list, body);
        }
    }
}
