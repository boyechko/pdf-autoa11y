// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.HashSet;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Records which elements an evidence pass of {@link MistaggedListCheck} has claimed, so weaker
 * passes leave them alone. "Each element belongs to the strongest evidence that matched it" is
 * enforced by running the passes strongest-first, each consulting the registry before emitting.
 */
final class ClaimRegistry {

    private final Set<Integer> claimed = new HashSet<>();

    /** Claims an element for the current evidence pass. */
    void claim(PdfStructElem elem) {
        claimed.add(StructTree.objNum(elem));
    }

    /** Whether a stronger evidence pass has already claimed the element. */
    boolean isClaimed(PdfStructElem elem) {
        return claimed.contains(StructTree.objNum(elem));
    }
}
