// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoListItemsFix;

/**
 * {@code !SPLIT_LINES [spec]}: delegates to SplitIntoListItemsFix to split the element's lumped
 * blocks into one list item per line, in a new list built where the element is. The spec, when
 * given, is a line count or per-item counts.
 */
record SplitLinesInstruction(String spec) implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) throws Exception {
        SplitIntoListItemsFix.inPlace(element, spec).apply(ctx);
        return receipt(spec == null ? "SPLIT_LINES" : "SPLIT_LINES " + spec);
    }
}
