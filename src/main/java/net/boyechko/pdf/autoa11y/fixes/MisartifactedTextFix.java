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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts a scribbled Lbl signpost near a structure element whose neighboring content stream
 * contains mis-artifacted digit text. Does not modify the content stream — the scribble marks where
 * manual de-artifacting is needed.
 */
public class MisartifactedTextFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(MisartifactedTextFix.class);
    private static final int P_MISARTIFACTED = 13;

    private final int pageNum;
    private final PdfStructElem neighborElem;
    private final String text;

    public MisartifactedTextFix(int pageNum, PdfStructElem neighborElem, String text) {
        this.pageNum = pageNum;
        this.neighborElem = neighborElem;
        this.text = text;
    }

    @Override
    public int priority() {
        return P_MISARTIFACTED;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfStructElem parent = (PdfStructElem) neighborElem.getParent();
        if (parent == null) {
            logger.warn("Cannot insert signpost for '{}': neighbor has no parent", text);
            return;
        }

        String scribbleValue = "misartifacted " + text;

        // Skip if a signpost with this exact scribble already exists as a sibling
        for (IStructureNode kid : parent.getKids()) {
            if (kid instanceof PdfStructElem sibling) {
                DocValue.Scribble scribble = StructTree.getScribble(sibling);
                if (scribble != null && scribbleValue.equals(scribble.value())) {
                    logger.debug("Signpost already exists for '{}', skipping", text);
                    return;
                }
            }
        }

        int insertIdx = StructTree.findKidIndex(parent, neighborElem);
        PdfStructElem lbl = new PdfStructElem(ctx.doc(), PdfName.Lbl);
        parent.addKid(insertIdx, lbl);
        StructTree.setScribble(lbl, scribbleValue);
    }

    @Override
    public String describe() {
        return "Inserted signpost for mis-artifacted '" + text + "'";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(IssueLoc.atElem(ctx, neighborElem));
    }

    @Override
    public String groupLabel() {
        return "Scribbled Lbl signposts for mis-artifacted text";
    }
}
