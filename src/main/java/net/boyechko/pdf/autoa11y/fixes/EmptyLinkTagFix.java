// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Geometry;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Moves a sibling MCR into a Link tag. */
public class EmptyLinkTagFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(EmptyLinkTagFix.class);

    private final int linkObjNum;
    private final int mcid;
    private final int pageNum;

    public EmptyLinkTagFix(PdfStructElem linkElem, int mcid, int pageNum) {
        this.linkObjNum = StructTree.objNum(linkElem);
        this.mcid = mcid;
        this.pageNum = pageNum;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (linkObjNum <= 0 || mcid < 0) {
            return;
        }
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }

        PdfStructElem linkElem = StructTree.findByObjNumber(root, linkObjNum);
        if (linkElem == null) {
            logger.debug("Link element not found for obj. #{}", linkObjNum);
            return;
        }
        if (!StructTree.childrenOf(linkElem, PdfMcr.class).isEmpty()) {
            return;
        }

        List<PdfObjRef> objRefs = StructTree.childrenOf(linkElem, PdfObjRef.class);
        PdfObjRef objRef = objRefs.isEmpty() ? null : objRefs.getFirst();
        if (objRef == null) {
            return;
        }
        PdfDictionary annotDict = objRef.getReferencedObject();
        if (annotDict == null) {
            return;
        }

        PdfStructElem parent = linkElem.getParent() instanceof PdfStructElem p ? p : null;
        if (parent == null) {
            return;
        }

        List<IStructureNode> parentKids = parent.getKids();
        if (parentKids == null || parentKids.isEmpty()) {
            return;
        }

        int linkIndex = findIndex(parentKids, linkElem);
        if (linkIndex <= 0) {
            return;
        }

        IStructureNode prev = parentKids.get(linkIndex - 1);
        if (!(prev instanceof PdfMcr mcr) || mcr.getMcid() != mcid) {
            return;
        }

        int resolvedPageNum = pageNum > 0 ? pageNum : StructTree.pageOf(linkElem, ctx);
        if (resolvedPageNum <= 0 || resolvedPageNum > ctx.doc().getNumberOfPages()) {
            return;
        }

        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        resolvedPageNum,
                        () -> Content.extractBoundsForPage(ctx.doc().getPage(resolvedPageNum)));
        Rectangle mcrRect = mcidBounds.get(mcid);
        Rectangle annotRect = Geometry.getAnnotationBounds(annotDict);
        if (!Geometry.boundsSimilar(mcrRect, annotRect)) {
            return;
        }

        IStructureNode removed = parent.removeKid(linkIndex - 1);
        if (removed instanceof PdfMcr removedMcr) {
            linkElem.addKid(0, removedMcr);
        }
    }

    private int findIndex(List<IStructureNode> kids, PdfStructElem target) {
        for (int i = 0; i < kids.size(); i++) {
            IStructureNode kid = kids.get(i);
            if (kid instanceof PdfStructElem elem && StructTree.isSameElement(elem, target)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String describe() {
        return "Moved sibling MCR into Link";
    }

    @Override
    public String describe(DocContext ctx) {
        String text = ctx.getMcidText(pageNum, mcid);
        if (text.isEmpty()) {
            return describe();
        }
        String truncated = text.length() > 30 ? text.substring(0, 29) + "…" : text;
        return "Moved sibling MCR \"" + truncated + "\" into Link";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        if (pageNum > 0 && mcid >= 0) {
            return new IssueMsg(
                    describe(ctx), IssueLoc.atMcid(pageNum, mcid, linkObjNum, "Link", null));
        }
        if (linkObjNum > 0) {
            return new IssueMsg(
                    describe(ctx),
                    IssueLoc.atObj(
                            linkObjNum,
                            pageNum > 0 ? pageNum : null,
                            IssueLoc.ObjKind.STRUCT_ELEM));
        }
        return new IssueMsg(describe(ctx), IssueLoc.none());
    }
}
