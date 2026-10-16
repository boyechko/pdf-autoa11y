// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.MistaggedArtifactFix;

/**
 * {@code !ARTIFACT}: delegates to MistaggedArtifactFix to convert the element's subtree to
 * artifacts. No receipt is written, since the element is removed from the tree.
 */
record ArtifactInstruction() implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) throws Exception {
        new MistaggedArtifactFix(element).apply(ctx);
        return null;
    }

    @Override
    public boolean coversSubtree() {
        return true;
    }
}
