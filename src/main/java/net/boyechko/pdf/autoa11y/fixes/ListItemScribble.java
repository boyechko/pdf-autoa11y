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
