// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue.Scribble;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts a scribbled Lbl signpost near a structure element whose neighboring content stream
 * contains mis-artifacted digit text. Does not modify the content stream — the scribble marks where
 * manual de-artifacting is needed.
 */
public class MisartifactedTextFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(MisartifactedTextFix.class);

    private final int pageNum;
    private final PdfStructElem neighborElem;
    private final String text;

    public MisartifactedTextFix(int pageNum, PdfStructElem neighborElem, String text) {
        this.pageNum = pageNum;
        this.neighborElem = neighborElem;
        this.text = text;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfStructElem parent = (PdfStructElem) neighborElem.getParent();
        if (parent == null) {
            logger.warn("Cannot insert signpost for '{}': neighbor has no parent", text);
            return;
        }

        Scribble signpost = Scribble.user("misartifacted " + text);

        // Skip if a signpost with this exact scribble already exists as a sibling
        for (Tag sibling : Tag.of(parent).children()) {
            if (signpost.equals(sibling.scribble())) {
                logger.debug("Signpost already exists for '{}', skipping", text);
                return;
            }
        }

        PdfStructElem lbl = new PdfStructElem(ctx.doc(), PdfName.Lbl);
        Tag.of(lbl).insertBefore(Tag.of(neighborElem));
        Tag.of(lbl).scribble(signpost);
    }

    @Override
    public String describe() {
        return "Inserted signpost for mis-artifacted '" + text + "'";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(Locus.atElem(ctx, neighborElem));
    }

    @Override
    public String groupLabel() {
        return "Scribbled Lbl signposts for mis-artifacted text";
    }
}
