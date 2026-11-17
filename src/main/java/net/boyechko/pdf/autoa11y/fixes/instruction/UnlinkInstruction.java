// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Annotation;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.MarkedContent;
import net.boyechko.pdf.autoa11y.document.ObjectRef;
import net.boyechko.pdf.autoa11y.document.StructNode;
import net.boyechko.pdf.autoa11y.document.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code !UNLINK}: unwraps a Link struct element. Promotes its non-OBJR kids to its parent at its
 * original position, removes the Link element, and deletes the associated Link annotation from its
 * page's /Annots array. The element is destroyed, so no receipt is written.
 */
record UnlinkInstruction() implements ScribbledInstruction {
    private static final Logger logger = LoggerFactory.getLogger(UnlinkInstruction.class);

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        if (!PdfName.Link.equals(element.getRole())) {
            throw new IllegalArgumentException(
                    "!UNLINK requires a Link element, got: " + element.getRole());
        }

        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot unlink: element has no parent");
            return null;
        }
        Tag link = Tag.of(element);
        if (!link.isAttached()) {
            logger.warn("Cannot unlink: element not found in parent's kids");
            return null;
        }

        List<StructNode> origKids = link.kids();

        PdfDictionary annotDict = null;
        for (StructNode kid : origKids) {
            if (kid instanceof ObjectRef ref) {
                PdfObject refObj = ref.objRef().getReferencedObject();
                if (refObj instanceof PdfDictionary dict) {
                    annotDict = dict;
                    break;
                }
            }
        }

        PdfObject linkEffectivePg = link.pageEntry();

        for (StructNode kid : origKids) {
            if (kid instanceof ObjectRef) {
                continue;
            }
            // Bare-int MCRs resolve their page via ancestor /Pg. If the parent lacks an
            // explicit /Pg, set it now so the promoted MCR can still find its page.
            if (kid instanceof MarkedContent content
                    && content.mcr() instanceof PdfMcrNumber
                    && linkEffectivePg != null
                    && parent.getPdfObject().get(PdfName.Pg) == null) {
                parent.getPdfObject().put(PdfName.Pg, linkEffectivePg);
                parent.setModified();
            }
            kid.insertBefore(link);
        }

        Tag.of(element).remove();

        if (annotDict != null && !Annotation.removeFromAnyPage(ctx.doc(), annotDict)) {
            int objNum =
                    annotDict.getIndirectReference() != null
                            ? annotDict.getIndirectReference().getObjNumber()
                            : 0;
            logger.debug("Link annotation {} not found in any page /Annots", Format.objNum(objNum));
        }
        return null;
    }
}
