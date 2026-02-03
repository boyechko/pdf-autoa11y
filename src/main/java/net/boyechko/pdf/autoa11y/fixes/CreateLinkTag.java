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
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.content.McidBoundsExtractor;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a Link tag for an annotation. */
public class CreateLinkTag implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(CreateLinkTag.class);
    // After SetupDocumentStructure (10), so Parts exist
    private static final int P_CREATE_LINK = 22;

    private final PdfDictionary annotDict;
    private final int pageNum;

    public CreateLinkTag(PdfDictionary annotDict, int pageNum) {
        this.annotDict = annotDict;
        this.pageNum = pageNum;
    }

    @Override
    public int priority() {
        return P_CREATE_LINK;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        PdfStructElem documentElem = findDocumentElement(root);
        if (documentElem == null) {
            throw new IllegalStateException("No Document element found");
        }

        PdfPage page = ctx.doc().getPage(pageNum);
        PdfAnnotation annotation = findMatchingAnnotation(page, annotDict);
        if (annotation == null) {
            logger.debug("Could not find annotation on page {}", pageNum);
            return;
        }

        PdfStructElem parentElem = findBestParentForAnnotation(ctx, documentElem, page, annotation);
        if (parentElem == null) {
            parentElem = documentElem;
        }

        PdfStructElem linkElem = new PdfStructElem(ctx.doc(), PdfName.Link, page);
        parentElem.addKid(linkElem);

        int structParentIndex = ctx.doc().getNextStructParentIndex();
        PdfObjRef objRef = new PdfObjRef(annotation, linkElem, structParentIndex);
        linkElem.addKid(objRef);

        int annotObjNum =
                annotDict.getIndirectReference() != null
                        ? annotDict.getIndirectReference().getObjNumber()
                        : 0;
        logger.debug(
                "Created Link tag for annotation obj #{} on page {} with StructParent {}",
                annotObjNum,
                pageNum,
                structParentIndex);
    }

    private PdfStructElem findDocumentElement(PdfStructTreeRoot root) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return null;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                PdfName role = elem.getRole();
                if (role != null && "Document".equals(role.getValue())) {
                    return elem;
                }
            }
        }
        return null;
    }

    private PdfAnnotation findMatchingAnnotation(PdfPage page, PdfDictionary targetDict) {
        for (PdfAnnotation annot : page.getAnnotations()) {
            PdfDictionary annotPdfObj = annot.getPdfObject();

            if (annotPdfObj == targetDict) {
                return annot;
            }
            if (targetDict.getIndirectReference() != null
                    && targetDict
                            .getIndirectReference()
                            .equals(annotPdfObj.getIndirectReference())) {
                return annot;
            }
        }
        return null;
    }

    private PdfStructElem findBestParentForAnnotation(
            DocumentContext ctx, PdfStructElem partElem, PdfPage page, PdfAnnotation annotation) {
        Rectangle annotBounds = getAnnotationBounds(annotation);
        if (annotBounds == null) {
            return null;
        }
        double annotArea = area(annotBounds);
        if (annotArea <= 0) {
            return null;
        }

        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        pageNum, () -> McidBoundsExtractor.extractBoundsForPage(page));
        if (mcidBounds.isEmpty()) {
            return null;
        }

        Map<PdfStructElem, Rectangle> elemBounds = new HashMap<>();
        Map<PdfStructElem, Integer> elemDepths = new HashMap<>();
        collectBounds(ctx, partElem, page, mcidBounds, elemBounds, elemDepths, 0);
        if (elemBounds.isEmpty()) {
            return null;
        }

        PdfStructElem bestElem = null;
        double bestScore = 0.0;
        int bestDepth = -1;
        double bestArea = Double.MAX_VALUE;

        for (Map.Entry<PdfStructElem, Rectangle> entry : elemBounds.entrySet()) {
            PdfStructElem elem = entry.getKey();
            if (elem == partElem) {
                continue;
            }
            PdfName role = elem.getRole();
            if (role != null) {
                String roleValue = role.getValue();
                if ("Link".equals(roleValue) || "Reference".equals(roleValue)) {
                    continue;
                }
            }

            Rectangle elemRect = entry.getValue();
            Rectangle intersection = elemRect.getIntersection(annotBounds);
            if (intersection == null) {
                continue;
            }
            double intersectionArea = area(intersection);
            if (intersectionArea <= 0) {
                continue;
            }

            double score = intersectionArea / annotArea;
            int depth = elemDepths.getOrDefault(elem, 0);
            double elemArea = area(elemRect);

            // Prefer higher score, then deeper elements, then smaller area
            if (score > bestScore + 1e-6) {
                bestScore = score;
                bestDepth = depth;
                bestArea = elemArea;
                bestElem = elem;
            } else if (Math.abs(score - bestScore) < 1e-6) {
                if (depth > bestDepth) {
                    bestDepth = depth;
                    bestArea = elemArea;
                    bestElem = elem;
                } else if (depth == bestDepth && elemArea < bestArea) {
                    bestArea = elemArea;
                    bestElem = elem;
                }
            }
        }

        if (bestElem == null || bestScore < 0.3) {
            return null;
        }

        return bestElem;
    }

    // TODO: Move to a utility class or @McidBoundsExtractor
    private Rectangle collectBounds(
            DocumentContext ctx,
            PdfStructElem elem,
            PdfPage targetPage,
            Map<Integer, Rectangle> mcidBounds,
            Map<PdfStructElem, Rectangle> elemBounds,
            Map<PdfStructElem, Integer> elemDepths,
            int depth) {
        PdfDictionary elemPg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (elemPg != null && !isSamePage(elemPg, targetPage)) {
            return null;
        }

        List<IStructureNode> kids = elem.getKids();
        if (kids == null) {
            return null;
        }

        Rectangle bounds = null;
        for (IStructureNode kid : kids) {
            if (kid == null) {
                continue;
            }
            if (kid instanceof PdfMcr mcr) {
                int mcid = mcr.getMcid();
                if (mcid >= 0) {
                    Rectangle mcrRect = mcidBounds.get(mcid);
                    if (mcrRect != null) {
                        bounds = union(bounds, mcrRect);
                    }
                }
            } else if (kid instanceof PdfStructElem childElem) {
                Rectangle childBounds =
                        collectBounds(
                                ctx,
                                childElem,
                                targetPage,
                                mcidBounds,
                                elemBounds,
                                elemDepths,
                                depth + 1);
                if (childBounds != null) {
                    bounds = union(bounds, childBounds);
                }
            }
        }

        if (bounds != null) {
            elemBounds.put(elem, bounds);
            elemDepths.put(elem, depth);
        }

        return bounds;
    }

    // TODO: Move to a utility class
    private boolean isSamePage(PdfDictionary pgDict, PdfPage targetPage) {
        PdfDictionary targetDict = targetPage.getPdfObject();
        if (pgDict.equals(targetDict)) {
            return true;
        }
        if (pgDict.getIndirectReference() != null && targetDict.getIndirectReference() != null) {
            return pgDict.getIndirectReference().equals(targetDict.getIndirectReference());
        }
        return false;
    }

    // TODO: Move to a utility class or @McidBoundsExtractor
    private Rectangle getAnnotationBounds(PdfAnnotation annotation) {
        PdfDictionary dict = annotation.getPdfObject();
        Rectangle quadBounds = getQuadPointsBounds(dict);
        if (quadBounds != null) {
            return quadBounds;
        }
        return getRectBounds(dict);
    }

    // TODO: Move to a utility class or @McidBoundsExtractor
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

    // TODO: Move to a utility class or @McidBoundsExtractor
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

    // TODO: Move to a utility class
    private Rectangle union(Rectangle a, Rectangle b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Rectangle.getCommonRectangle(a, b);
    }

    // TODO: Move to a utility class
    private double area(Rectangle rect) {
        if (rect == null) {
            return 0;
        }
        double width = Math.max(0.0, rect.getWidth());
        double height = Math.max(0.0, rect.getHeight());
        return width * height;
    }

    @Override
    public String describe() {
        int annotObjNum =
                annotDict.getIndirectReference() != null
                        ? annotDict.getIndirectReference().getObjNumber()
                        : 0;
        return "Created Link tag for annotation #" + annotObjNum + " on page " + pageNum;
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }

    @Override
    public String groupLabel() {
        return "Link tags created";
    }
}
