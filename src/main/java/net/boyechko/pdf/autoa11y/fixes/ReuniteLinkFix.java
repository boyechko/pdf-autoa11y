// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Reunites a Link tag holding only a link annotation with the Link tag elsewhere in the tree that
 * holds only the link's text: the annotation's OBJRs move into the text Link, which keeps its place
 * in reading order, and the emptied Link is pruned together with any wrappers left empty above it.
 *
 * <p>Applies only while the annotation Link still has no text and the text Link no annotation, so a
 * second run is a no-op.
 */
public final class ReuniteLinkFix implements IssueFix {

    private final PdfStructElem annotLink;
    private final PdfStructElem textLink;
    private final int pageNum;

    public ReuniteLinkFix(PdfStructElem annotLink, PdfStructElem textLink, int pageNum) {
        this.annotLink = annotLink;
        this.textLink = textLink;
        this.pageNum = pageNum;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        List<PdfObjRef> objRefs = StructTree.childrenOf(annotLink, PdfObjRef.class);
        if (objRefs.isEmpty()
                || !StructTree.descendantsOf(annotLink, PdfMcr.class).isEmpty()
                || !StructTree.descendantsOf(textLink, PdfObjRef.class).isEmpty()) {
            return;
        }
        for (PdfObjRef objRef : objRefs) {
            StructTree.moveKid(objRef, annotLink, textLink);
        }
        StructTree.pruneEmpty(annotLink);
    }

    @Override
    public String describe() {
        return "Moved link annotation into the Link holding its text";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(
                describe(ctx),
                Locus.atObj(StructTree.objNum(textLink), pageNum, Locus.ObjKind.STRUCT_ELEM));
    }
}
