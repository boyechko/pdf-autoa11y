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
package net.boyechko.pdf.autoa11y.validation.visitors;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.content.McidBoundsExtractor;
import net.boyechko.pdf.autoa11y.fixes.MoveSiblingMcrIntoLink;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/** Visitor that detects empty Link tags that should contain marked content from a sibling MCR. */
public class EmptyLinkTagVisitor implements StructureTreeVisitor {

    private static final double AREA_RATIO_MIN = 0.5;
    private static final double AREA_RATIO_MAX = 2.0;

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Empty Link Tag Check";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        // Get the raw IStructureNode children (includes MCRs, not just PdfStructElems)
        List<IStructureNode> kids = ctx.node().getKids();
        if (kids == null || kids.isEmpty()) {
            return true;
        }

        // Look for empty Link elements that follow MCRs
        for (int i = 1; i < kids.size(); i++) {
            IStructureNode prev = kids.get(i - 1);
            IStructureNode curr = kids.get(i);

            if (!(curr instanceof PdfStructElem linkElem)) {
                continue;
            }
            if (!isLinkRole(linkElem) || linkHasMcr(linkElem)) {
                continue;
            }
            if (!(prev instanceof PdfMcr mcr) || mcr.getMcid() < 0) {
                continue;
            }

            PdfObjRef objRef = findObjRef(linkElem);
            if (objRef == null) {
                continue;
            }
            PdfDictionary annotDict = objRef.getReferencedObject();
            if (annotDict == null) {
                continue;
            }

            int pageNum = ctx.getPageNumber();
            if (pageNum <= 0) {
                continue;
            }

            Map<Integer, Rectangle> mcidBounds =
                    ctx.docCtx()
                            .getOrComputeMcidBounds(
                                    pageNum,
                                    () ->
                                            McidBoundsExtractor.extractBoundsForPage(
                                                    ctx.doc().getPage(pageNum)));
            Rectangle mcrRect = mcidBounds.get(mcr.getMcid());
            Rectangle annotRect = getAnnotationBounds(annotDict);
            if (!boundsSimilar(mcrRect, annotRect)) {
                continue;
            }

            IssueFix fix = new MoveSiblingMcrIntoLink(linkElem, mcr.getMcid(), pageNum);
            Issue issue =
                    new Issue(
                            IssueType.EMPTY_LINK_TAG_RULE,
                            IssueSeverity.WARNING,
                            new IssueLocation(pageNum, "Page " + pageNum),
                            "Link tag (after/inside "
                                    + prev.getRole().getValue()
                                    + ") is missing content",
                            fix);
            issues.add(issue);
        }

        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private boolean isLinkRole(PdfStructElem elem) {
        PdfName role = elem.getRole();
        return role != null && "Link".equals(role.getValue());
    }

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

    private Rectangle getAnnotationBounds(PdfDictionary annotDict) {
        Rectangle quadBounds = getQuadPointsBounds(annotDict);
        if (quadBounds != null) {
            return quadBounds;
        }
        return getRectBounds(annotDict);
    }

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
        Rectangle intersection = mcrRect.getIntersection(annotRect);
        if (intersection == null) {
            return false;
        }
        return ratio >= AREA_RATIO_MIN && ratio <= AREA_RATIO_MAX;
    }

    private double area(Rectangle rect) {
        double width = Math.max(0.0, rect.getWidth());
        double height = Math.max(0.0, rect.getHeight());
        return width * height;
    }
}
