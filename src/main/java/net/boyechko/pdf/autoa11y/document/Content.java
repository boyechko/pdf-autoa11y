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

import com.itextpdf.kernel.geom.IShape;
import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Path;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.geom.Subpath;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.PathRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Extracts text and bounds from PDF content streams, keyed by MCID. */
public final class Content {
    private static final Logger logger = LoggerFactory.getLogger(Content.class);
    private static final double ARTIFICIAL_SPACING_RATIO = 0.3;

    /** The kind of content found in a marked content section. */
    public enum ContentKind {
        TEXT,
        IMAGE
    }

    private Content() {}

    // ── Content kind extraction ─────────────────────────────────────────

    /** Determines the content kind (text, image, or both) for each MCID on a page. */
    public static Map<Integer, Set<ContentKind>> extractContentKindsForPage(PdfPage page) {
        Map<Integer, Set<ContentKind>> result = new HashMap<>();
        if (page == null) {
            return result;
        }

        try {
            ContentKindListener listener = new ContentKindListener(result);
            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);
        } catch (Exception e) {
            int pageNum = page.getDocument().getPageNumber(page);
            logger.debug(
                    "Failed to extract content kinds for page {}: {}", pageNum, e.getMessage());
        }

        return result;
    }

    /** Listener that tracks whether each MCID contains text, images, or both. */
    private static class ContentKindListener implements IEventListener {
        private final Map<Integer, Set<ContentKind>> kindsByMcid;

        ContentKindListener(Map<Integer, Set<ContentKind>> kindsByMcid) {
            this.kindsByMcid = kindsByMcid;
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo textInfo = (TextRenderInfo) data;
                Integer mcid = textInfo.getMcid();
                if (mcid != null && mcid >= 0) {
                    String text = textInfo.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        kindsByMcid
                                .computeIfAbsent(mcid, k -> EnumSet.noneOf(ContentKind.class))
                                .add(ContentKind.TEXT);
                    }
                }
            } else if (type == EventType.RENDER_IMAGE) {
                ImageRenderInfo imageInfo = (ImageRenderInfo) data;
                int mcid = imageInfo.getMcid();
                if (mcid >= 0) {
                    kindsByMcid
                            .computeIfAbsent(mcid, k -> EnumSet.noneOf(ContentKind.class))
                            .add(ContentKind.IMAGE);
                }
            }
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_TEXT, EventType.RENDER_IMAGE);
        }
    }

    // ── Bullet glyph detection ─────────────────────────────────────────

    /** Position of a detected bullet glyph in page coordinates. */
    public record BulletPosition(float x, float y) {}

    /**
     * Scans a page's content stream for Bézier-circle bullet glyphs drawn as artifacts. Matches the
     * specific two-curve circle pattern produced by web-to-PDF converters:
     *
     * <pre>
     * 0 0 m
     * 0 2.5 -3.75 2.5 -3.75 0 c
     * -3.75 -2.5 0 -2.5 0 0 c
     * </pre>
     *
     * The untransformed path spans roughly 3.75 × 5 pt. The CTM positions each bullet on the page.
     */
    public static List<BulletPosition> extractBulletPositionsForPage(PdfPage page) {
        List<BulletPosition> bullets = new ArrayList<>();
        if (page == null) {
            return bullets;
        }

        try {
            BulletGlyphListener listener = new BulletGlyphListener(bullets);
            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);
        } catch (Exception e) {
            int pageNum = page.getDocument().getPageNumber(page);
            logger.debug(
                    "Failed to extract bullet positions for page {}: {}", pageNum, e.getMessage());
        }

        return bullets;
    }

    /**
     * Listener that detects the specific two-cubic-Bézier circle pattern used as bullet glyphs in
     * artifact content. The path must have exactly one subpath with exactly two cubic Bézier
     * segments, and the untransformed bounding box must be approximately 3.75 × 5 pt.
     */
    private static class BulletGlyphListener implements IEventListener {
        /** Expected untransformed width of the bullet circle (3.75 pt). */
        private static final float EXPECTED_WIDTH = 3.75f;

        /** Expected untransformed height of the bullet circle (5.0 pt). */
        private static final float EXPECTED_HEIGHT = 5.0f;

        /** Tolerance for matching expected dimensions. */
        private static final float DIMENSION_TOLERANCE = 1.0f;

        private static final float DEDUP_TOLERANCE = 1.0f;

        private final List<BulletPosition> bullets;

        BulletGlyphListener(List<BulletPosition> bullets) {
            this.bullets = bullets;
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type != EventType.RENDER_PATH) {
                return;
            }
            PathRenderInfo pathInfo = (PathRenderInfo) data;

            // Only consider artifacts (not inside marked content)
            if (pathInfo.getMcid() >= 0) {
                return;
            }

            // Must be painted (filled, stroked, or both)
            if (pathInfo.getOperation() == PathRenderInfo.NO_OP) {
                return;
            }

            Path path = pathInfo.getPath();
            if (!isBezierCircle(path)) {
                return;
            }

            // Compute center in page coordinates via CTM
            Matrix ctm = pathInfo.getCtm();
            Point start = path.getSubpaths().get(0).getStartPoint();
            Vector center = new Vector((float) start.getX(), (float) start.getY(), 1).cross(ctm);
            float cx = center.get(Vector.I1);
            float cy = center.get(Vector.I2);

            // Deduplicate fill+stroke pairs for the same bullet
            boolean duplicate =
                    bullets.stream()
                            .anyMatch(
                                    b ->
                                            Math.abs(b.x() - cx) < DEDUP_TOLERANCE
                                                    && Math.abs(b.y() - cy) < DEDUP_TOLERANCE);
            if (!duplicate) {
                bullets.add(new BulletPosition(cx, cy));
            }
        }

        /** Checks if a path is a two-cubic-Bézier circle of the expected bullet dimensions. */
        private boolean isBezierCircle(Path path) {
            List<Subpath> subpaths = path.getSubpaths();
            if (subpaths.size() != 1) {
                return false;
            }

            Subpath subpath = subpaths.get(0);
            List<IShape> segments = subpath.getSegments();
            if (segments.size() != 2) {
                return false;
            }

            // Both segments must be cubic Bézier curves (4 base points each)
            for (IShape segment : segments) {
                if (!(segment instanceof com.itextpdf.kernel.geom.BezierCurve)) {
                    return false;
                }
                if (segment.getBasePoints().size() != 4) {
                    return false;
                }
            }

            // Check untransformed bounding box matches expected bullet dimensions
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

            Point start = subpath.getStartPoint();
            if (start != null) {
                minX = Math.min(minX, (float) start.getX());
                minY = Math.min(minY, (float) start.getY());
                maxX = Math.max(maxX, (float) start.getX());
                maxY = Math.max(maxY, (float) start.getY());
            }
            for (IShape segment : segments) {
                for (Point pt : segment.getBasePoints()) {
                    minX = Math.min(minX, (float) pt.getX());
                    minY = Math.min(minY, (float) pt.getY());
                    maxX = Math.max(maxX, (float) pt.getX());
                    maxY = Math.max(maxY, (float) pt.getY());
                }
            }

            float width = maxX - minX;
            float height = maxY - minY;

            return Math.abs(width - EXPECTED_WIDTH) <= DIMENSION_TOLERANCE
                    && Math.abs(height - EXPECTED_HEIGHT) <= DIMENSION_TOLERANCE;
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_PATH);
        }
    }

    // ── Text extraction ─────────────────────────────────────────────────

    /** Extracts text for all MCIDs on a page in a single content-stream pass. */
    public static Map<Integer, String> extractTextForPage(PdfPage page) {
        Map<Integer, String> result = new HashMap<>();
        if (page == null) {
            return result;
        }

        try {
            McidTextListener listener = new McidTextListener();
            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);

            for (Map.Entry<Integer, StringBuilder> entry : listener.textByMcid.entrySet()) {
                String cleaned = cleanExtractedText(entry.getValue().toString());
                if (!cleaned.isEmpty()) {
                    result.put(entry.getKey(), cleaned);
                }
            }
        } catch (Exception e) {
            int pageNum = page.getDocument().getPageNumber(page);
            logger.debug("Failed to extract MCID text for page {}: {}", pageNum, e.getMessage());
        }

        return result;
    }

    /** Gets the text content for all MCRs within a structure element. */
    public static String getTextForElement(PdfStructElem node, DocumentContext ctx, int pageNum) {
        return getTextForElement(node, ctx.getMcidText(pageNum));
    }

    /** Gets the text content for all MCRs within a structure element, recursing into children. */
    public static String getTextForElement(PdfStructElem node, Map<Integer, String> mcidText) {
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return "";

        StringBuilder combinedText = new StringBuilder();
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfMcrNumber mcr) {
                String text = mcidText.getOrDefault(mcr.getMcid(), "");
                if (!text.isEmpty()) {
                    if (combinedText.length() > 0) combinedText.append(" ");
                    combinedText.append(text);
                }
            } else if (kid instanceof PdfStructElem childElem) {
                String childText = getTextForElement(childElem, mcidText);
                if (!childText.isEmpty()) {
                    if (combinedText.length() > 0) combinedText.append(" ");
                    combinedText.append(childText);
                }
            }
        }

        return combinedText.toString();
    }

    /** Collects text for every MCID encountered on a page. */
    private static class McidTextListener implements IEventListener {
        private final Map<Integer, StringBuilder> textByMcid = new HashMap<>();

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo textInfo = (TextRenderInfo) data;
                Integer mcid = textInfo.getMcid();
                if (mcid != null && mcid >= 0) {
                    String text = textInfo.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        StringBuilder sb =
                                textByMcid.computeIfAbsent(mcid, k -> new StringBuilder());
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(text);
                    }
                }
            }
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_TEXT);
        }
    }

    /** Cleans extracted text by removing replacement characters and normalizing whitespace. */
    private static String cleanExtractedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove Unicode replacement character (U+FFFD)
        String cleaned = text.replace("\uFFFD", "");

        // Check if text has artificial character spacing (more than 30% single-char words)
        if (hasArtificialSpacing(cleaned)) {
            // Remove spaces between single characters
            cleaned = cleaned.replaceAll("(?<=\\S) (?=\\S)", "");
        }

        // Normalize whitespace
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /** Detects if text has artificial character-by-character spacing. */
    private static boolean hasArtificialSpacing(String text) {
        String[] words = text.split("\\s+");
        if (words.length < 2) {
            return false; // Not enough data to determine
        }

        // Count single-character "words"
        long singleCharWords = Arrays.stream(words).filter(w -> w.length() == 1).count();

        // If more than 30% are single characters, assume artificial spacing
        double ratio = (double) singleCharWords / words.length;
        return ratio > ARTIFICIAL_SPACING_RATIO;
    }

    /** Gets the bounding box for a single MCR by its MCID. */
    public static Rectangle getBoundsForMcid(DocumentContext ctx, int pageNum, int mcid) {
        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        pageNum, () -> extractBoundsForPage(ctx.doc().getPage(pageNum)));
        return mcidBounds.get(mcid);
    }

    /** Gets the union bounding box for all MCRs within a structure element. */
    public static Rectangle getBoundsForElement(
            PdfStructElem node, DocumentContext ctx, int pageNum) {
        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        pageNum, () -> extractBoundsForPage(ctx.doc().getPage(pageNum)));

        Rectangle result = null;
        for (PdfMcr mcr : StructureTree.collectMcrs(node)) {
            if (mcr instanceof PdfObjRef) {
                continue;
            }
            int mcid = mcr.getMcid();
            if (mcid < 0) {
                continue;
            }
            Rectangle bounds = mcidBounds.get(mcid);
            if (bounds != null) {
                result = Geometry.union(result, bounds);
            }
        }
        return result;
    }

    // ── Bounds extraction ───────────────────────────────────────────────

    public static Map<Integer, Rectangle> extractBoundsForPage(PdfPage page) {
        Map<Integer, Rectangle> bounds = new HashMap<>();
        if (page == null) {
            return bounds;
        }

        try {
            McidBoundsListener listener = new McidBoundsListener(bounds);
            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);
        } catch (Exception e) {
            int pageNum = page.getDocument().getPageNumber(page);
            logger.debug("Failed to extract MCID bounds for page {}: {}", pageNum, e.getMessage());
        }

        return bounds;
    }

    private static class McidBoundsListener implements IEventListener {
        private final Map<Integer, Rectangle> bounds;

        private McidBoundsListener(Map<Integer, Rectangle> bounds) {
            this.bounds = bounds;
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo textInfo = (TextRenderInfo) data;
                Integer mcid = textInfo.getMcid();
                if (mcid != null && mcid >= 0) {
                    addBounds(bounds, mcid, rectFromText(textInfo));
                }
            } else if (type == EventType.RENDER_IMAGE) {
                ImageRenderInfo imageInfo = (ImageRenderInfo) data;
                int mcid = imageInfo.getMcid();
                if (mcid >= 0) {
                    addBounds(bounds, mcid, rectFromImage(imageInfo));
                }
            }
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_TEXT, EventType.RENDER_IMAGE);
        }
    }

    private static void addBounds(Map<Integer, Rectangle> bounds, int mcid, Rectangle rect) {
        if (rect == null) {
            return;
        }
        Rectangle existing = bounds.get(mcid);
        bounds.put(mcid, Geometry.union(existing, rect));
    }

    private static Rectangle rectFromText(TextRenderInfo info) {
        String text = info.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        LineSegment ascent = info.getAscentLine();
        LineSegment descent = info.getDescentLine();
        return rectFromPoints(
                ascent.getStartPoint(),
                ascent.getEndPoint(),
                descent.getStartPoint(),
                descent.getEndPoint());
    }

    private static Rectangle rectFromImage(ImageRenderInfo info) {
        Matrix ctm = info.getImageCtm();
        if (ctm == null) {
            return null;
        }
        Vector p0 = new Vector(0, 0, 1).cross(ctm);
        Vector p1 = new Vector(1, 0, 1).cross(ctm);
        Vector p2 = new Vector(1, 1, 1).cross(ctm);
        Vector p3 = new Vector(0, 1, 1).cross(ctm);
        return rectFromPoints(p0, p1, p2, p3);
    }

    private static Rectangle rectFromPoints(Vector... points) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (Vector point : points) {
            if (point == null) {
                continue;
            }
            float x = point.get(Vector.I1);
            float y = point.get(Vector.I2);
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
}
