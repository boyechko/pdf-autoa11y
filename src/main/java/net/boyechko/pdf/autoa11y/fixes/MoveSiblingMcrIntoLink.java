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
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.McidBoundsExtractor;
import net.boyechko.pdf.autoa11y.document.McidTextExtractor;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Moves a sibling MCR into a Link tag. */
public class MoveSiblingMcrIntoLink implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(MoveSiblingMcrIntoLink.class);
    private static final int P_MOVE_LINK_CONTENT = 24;
    private static final double AREA_RATIO_MIN = 0.5;
    private static final double AREA_RATIO_MAX = 2.0;

    private final int linkObjNum;
    private final int mcid;
    private final int pageNum;

    public MoveSiblingMcrIntoLink(PdfStructElem linkElem, int mcid, int pageNum) {
        this.linkObjNum =
                linkElem.getPdfObject().getIndirectReference() != null
                        ? linkElem.getPdfObject().getIndirectReference().getObjNumber()
                        : 0;
        this.mcid = mcid;
        this.pageNum = pageNum;
    }

    @Override
    public int priority() {
        return P_MOVE_LINK_CONTENT;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        if (linkObjNum == 0 || mcid < 0) {
            return;
        }
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }

        PdfStructElem linkElem = findStructElemByObjNum(root, linkObjNum);
        if (linkElem == null) {
            logger.debug("Link element not found for obj #{}", linkObjNum);
            return;
        }
        if (linkHasMcr(linkElem)) {
            return;
        }

        PdfObjRef objRef = findObjRef(linkElem);
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

        int resolvedPageNum = pageNum > 0 ? pageNum : getPageNumber(linkElem, ctx);
        if (resolvedPageNum <= 0 || resolvedPageNum > ctx.doc().getNumberOfPages()) {
            return;
        }

        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        resolvedPageNum,
                        () ->
                                McidBoundsExtractor.extractBoundsForPage(
                                        ctx.doc().getPage(resolvedPageNum)));
        Rectangle mcrRect = mcidBounds.get(mcid);
        Rectangle annotRect = getAnnotationBounds(annotDict);
        if (!boundsSimilar(mcrRect, annotRect)) {
            return;
        }

        IStructureNode removed = parent.removeKid(linkIndex - 1);
        if (removed instanceof PdfMcr removedMcr) {
            linkElem.addKid(0, removedMcr);
        }
    }

    // TODO: Move to a utility class
    private PdfStructElem findStructElemByObjNum(PdfStructTreeRoot root, int objNum) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) {
            return null;
        }
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                PdfStructElem found = findStructElemByObjNum(elem, objNum);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // TODO: Move to a utility class
    private PdfStructElem findStructElemByObjNum(PdfStructElem elem, int objNum) {
        if (elem.getPdfObject().getIndirectReference() != null
                && elem.getPdfObject().getIndirectReference().getObjNumber() == objNum) {
            return elem;
        }
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) {
            return null;
        }
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem child) {
                PdfStructElem found = findStructElemByObjNum(child, objNum);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // TODO: Move to a utility class
    private boolean linkHasMcr(PdfStructElem linkElem) {
        List<IStructureNode> kids = linkElem.getKids();
        if (kids == null) {
            return false;
        }
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfMcr mcr && mcr.getMcid() >= 0) {
                return true;
            }
        }
        return false;
    }

    // TODO: Move to a utility class
    private PdfObjRef findObjRef(PdfStructElem linkElem) {
        List<IStructureNode> kids = linkElem.getKids();
        if (kids == null) {
            return null;
        }
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfObjRef objRef) {
                return objRef;
            }
        }
        return null;
    }

    private int findIndex(List<IStructureNode> kids, PdfStructElem target) {
        for (int i = 0; i < kids.size(); i++) {
            IStructureNode kid = kids.get(i);
            if (kid instanceof PdfStructElem elem && sameStructElem(elem, target)) {
                return i;
            }
        }
        return -1;
    }

    // TODO: Move to a utility class (same as @isSameStructElem in @ConvertToArtifact?)
    private boolean sameStructElem(PdfStructElem a, PdfStructElem b) {
        if (a == b) {
            return true;
        }
        PdfDictionary aDict = a.getPdfObject();
        PdfDictionary bDict = b.getPdfObject();
        if (aDict == bDict) {
            return true;
        }
        return aDict.getIndirectReference() != null
                && aDict.getIndirectReference().equals(bDict.getIndirectReference());
    }

    // TODO: Move to a utility class (same as @getPageNumber in @DocumentContext?)
    private int getPageNumber(PdfStructElem elem, DocumentContext ctx) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg != null) {
            return ctx.doc().getPageNumber(pg);
        }
        if (elem.getPdfObject().getIndirectReference() != null) {
            int objNum = elem.getPdfObject().getIndirectReference().getObjNumber();
            return ctx.getPageNumber(objNum);
        }
        return 0;
    }

    // TODO: Move to a utility class (same as @getAnnotationBounds in @EmptyLinkTagVisitor?)
    private Rectangle getAnnotationBounds(PdfDictionary annotDict) {
        Rectangle quadBounds = getQuadPointsBounds(annotDict);
        if (quadBounds != null) {
            return quadBounds;
        }
        return getRectBounds(annotDict);
    }

    // TODO: Move to a utility class (same as @getQuadPointsBounds in @EmptyLinkTagVisitor?)
    private Rectangle getQuadPointsBounds(PdfDictionary annotDict) {
        PdfArray quadPoints = annotDict.getAsArray(PdfName.QuadPoints);
        if (quadPoints == null || quadPoints.size() < 8) {
            return null;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i + 1 < quadPoints.size(); i += 2) {
            if (quadPoints.getAsNumber(i) == null || quadPoints.getAsNumber(i + 1) == null) {
                continue;
            }
            float x = quadPoints.getAsNumber(i).floatValue();
            float y = quadPoints.getAsNumber(i + 1).floatValue();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) {
            return null;
        }

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    // TODO: Move to a utility class (same as @getRectBounds in @EmptyLinkTagVisitor?)
    private Rectangle getRectBounds(PdfDictionary annotDict) {
        PdfArray rectArray = annotDict.getAsArray(PdfName.Rect);
        if (rectArray == null || rectArray.size() < 4) {
            return null;
        }
        if (rectArray.getAsNumber(0) == null
                || rectArray.getAsNumber(1) == null
                || rectArray.getAsNumber(2) == null
                || rectArray.getAsNumber(3) == null) {
            return null;
        }
        float llx = rectArray.getAsNumber(0).floatValue();
        float lly = rectArray.getAsNumber(1).floatValue();
        float urx = rectArray.getAsNumber(2).floatValue();
        float ury = rectArray.getAsNumber(3).floatValue();
        float minX = Math.min(llx, urx);
        float minY = Math.min(lly, ury);
        float maxX = Math.max(llx, urx);
        float maxY = Math.max(lly, ury);
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    // TODO: Move to a utility class (same as @boundsSimilar in @EmptyLinkTagVisitor?)
    private boolean boundsSimilar(Rectangle mcrRect, Rectangle annotRect) {
        if (mcrRect == null || annotRect == null) {
            return false;
        }
        double mcrArea = area(mcrRect);
        double annotArea = area(annotRect);
        if (mcrArea <= 0 || annotArea <= 0) {
            return false;
        }
        double ratio = mcrArea / annotArea;
        if (ratio < AREA_RATIO_MIN || ratio > AREA_RATIO_MAX) {
            return false;
        }
        return mcrRect.getIntersection(annotRect) != null;
    }

    // TODO: Move to a utility class (same as @area in @EmptyLinkTagVisitor?)
    private double area(Rectangle rect) {
        double width = Math.max(0.0, rect.getWidth());
        double height = Math.max(0.0, rect.getHeight());
        return width * height;
    }

    @Override
    public String describe() {
        return "Moved sibling MCR into Link for annotation obj #" + linkObjNum;
    }

    @Override
    public String describe(DocumentContext ctx) {
        String text = McidTextExtractor.extractTextForMcid(ctx.doc(), mcid, pageNum);
        if (text.isEmpty()) {
            return describe();
        }
        String truncated = text.length() > 30 ? text.substring(0, 29) + "…" : text;
        return "Moved sibling MCR \"" + truncated + "\" into Link";
    }
}
