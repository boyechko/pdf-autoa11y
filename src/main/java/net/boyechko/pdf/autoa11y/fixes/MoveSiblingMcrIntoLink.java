/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
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

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.ContentExtractor;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Geometry;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Moves a sibling MCR into a Link tag. */
public class MoveSiblingMcrIntoLink implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(MoveSiblingMcrIntoLink.class);
    private static final int P_MOVE_LINK_CONTENT = 24;

    private final int linkObjNum;
    private final int mcid;
    private final int pageNum;

    public MoveSiblingMcrIntoLink(PdfStructElem linkElem, int mcid, int pageNum) {
        this.linkObjNum = StructureTree.objNumber(linkElem);
        this.mcid = mcid;
        this.pageNum = pageNum;
    }

    @Override
    public int priority() {
        return P_MOVE_LINK_CONTENT;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        if (linkObjNum <= 0 || mcid < 0) {
            return;
        }
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }

        PdfStructElem linkElem = StructureTree.findByObjNumber(root, linkObjNum);
        if (linkElem == null) {
            logger.debug("Link element not found for obj #{}", linkObjNum);
            return;
        }
        if (StructureTree.hasMcr(linkElem)) {
            return;
        }

        PdfObjRef objRef = StructureTree.findFirstObjRef(linkElem);
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

        int resolvedPageNum =
                pageNum > 0 ? pageNum : StructureTree.determinePageNumber(ctx, linkElem);
        if (resolvedPageNum <= 0 || resolvedPageNum > ctx.doc().getNumberOfPages()) {
            return;
        }

        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        resolvedPageNum,
                        () ->
                                ContentExtractor.extractBoundsForPage(
                                        ctx.doc().getPage(resolvedPageNum)));
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
            if (kid instanceof PdfStructElem elem && StructureTree.isSameElement(elem, target)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String describe() {
        return "Moved sibling MCR into Link for annotation obj #" + linkObjNum;
    }

    @Override
    public String describe(DocumentContext ctx) {
        String text = ctx.getMcidText(pageNum, mcid);
        if (text.isEmpty()) {
            return describe();
        }
        String truncated = text.length() > 30 ? text.substring(0, 29) + "…" : text;
        return "Moved sibling MCR \"" + truncated + "\" into Link";
    }
}
