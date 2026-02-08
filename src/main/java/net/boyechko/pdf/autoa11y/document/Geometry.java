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
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;

/** Geometry utilities for PDF rectangles and annotation bounds. */
public final class Geometry {
    private static final double AREA_RATIO_MIN = 0.5;
    private static final double AREA_RATIO_MAX = 2.0;
    private static final double RECT_EQUAL_TOLERANCE = 0.5;

    private Geometry() {}

    /** Returns the union of two rectangles, handling nulls. */
    public static Rectangle union(Rectangle a, Rectangle b) {
        if (a == null) return b;
        if (b == null) return a;
        return Rectangle.getCommonRectangle(a, b);
    }

    /** Computes the area of a rectangle, returning 0 for null or negative dimensions. */
    public static double area(Rectangle rect) {
        if (rect == null) return 0;
        double width = Math.max(0.0, rect.getWidth());
        double height = Math.max(0.0, rect.getHeight());
        return width * height;
    }

    /** Gets the bounding rectangle of an annotation from /QuadPoints or /Rect. */
    public static Rectangle getAnnotationBounds(PdfDictionary annotDict) {
        Rectangle quadBounds = getQuadPointsBounds(annotDict);
        if (quadBounds != null) return quadBounds;
        return getRectBounds(annotDict);
    }

    /** Gets bounds from a /QuadPoints array. */
    public static Rectangle getQuadPointsBounds(PdfDictionary annotDict) {
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

    /** Gets bounds from a /Rect array. */
    public static Rectangle getRectBounds(PdfDictionary annotDict) {
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

    /** Checks if two rectangles have similar area and overlap. */
    public static boolean boundsSimilar(Rectangle a, Rectangle b) {
        if (a == null || b == null) return false;
        double aArea = area(a);
        double bArea = area(b);
        if (aArea <= 0 || bArea <= 0) return false;
        double ratio = aArea / bArea;
        if (ratio < AREA_RATIO_MIN || ratio > AREA_RATIO_MAX) return false;
        return a.getIntersection(b) != null;
    }

    /** Checks if two PDF rectangle arrays are equal within tolerance. */
    public static boolean rectsEqual(PdfArray rect1, PdfArray rect2) {
        if (rect1 == null || rect2 == null) return false;
        if (rect1.size() != 4 || rect2.size() != 4) return false;
        for (int i = 0; i < 4; i++) {
            double v1 = rect1.getAsNumber(i).doubleValue();
            double v2 = rect2.getAsNumber(i).doubleValue();
            if (Math.abs(v1 - v2) > RECT_EQUAL_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}
