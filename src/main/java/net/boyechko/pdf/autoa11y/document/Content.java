// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.geom.IShape;
import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Path;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.geom.Subpath;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfDictionary;
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
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        IMAGE,
        PATH
    }

    /** MCID located on the specified page. */
    public record PageMcid(int pageNum, int mcid) {}

    /** Font and text for a contiguous run of same-font text within an MCID. */
    public record TextSpan(String fontName, float fontSize, String text) {}

    /** Aggregated text, font spans, and content kinds for a single MCID. */
    public record McidContent(String text, List<TextSpan> spans, Set<ContentKind> kinds) {
        /** Returns the font of the first span, typically the dominant/heading font. */
        public TextSpan dominantFont() {
            return spans.isEmpty() ? null : spans.getFirst();
        }
    }

    private Content() {}

    // == Content kind extraction =========================================

    /**
     * Returns the content for each MCR in a structure element (including descendants), keyed by
     * page-scoped MCID.
     */
    public static Map<PageMcid, McidContent> findMcidsForElem(PdfStructElem elem, DocContext ctx) {
        Map<PageMcid, McidContent> results = new LinkedHashMap<>();
        if (elem == null || ctx == null) {
            return results;
        }

        for (PdfMcr mcr : StructTree.descendantsOf(elem, PdfMcr.class)) {
            int mcid = mcr.getMcid();

            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) {
                continue;
            }
            int pageNum = ctx.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                continue;
            }

            McidContent mc = ctx.getMcidContent(pageNum).get(mcid);
            if (mc != null && !mc.kinds().isEmpty()) {
                results.put(new PageMcid(pageNum, mcid), mc);
            }
        }

        return results;
    }

    // == Bullet glyph detection =========================================

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
        /** Expected untransformed width and height of the bullet circle (3.75 pt and 5.0 pt). */
        private static final float EXPECTED_WIDTH = 3.75f;

        private static final float EXPECTED_HEIGHT = 5.0f;

        /** Tolerance for matching expected dimensions (1.0 pt). */
        private static final float DIMENSION_TOLERANCE = 1.0f;

        /** Tolerance for deduplicating duplicate bullets (1.0 pt). */
        private static final float DEDUP_TOLERANCE = 1.0f;

        /** List of detected bullet positions. */
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
            Point start =
                    path.getSubpaths().stream()
                            .map(Subpath::getStartPoint)
                            .filter(p -> p != null)
                            .findFirst()
                            .orElse(null);
            if (start == null) {
                return;
            }
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

        /**
         * Checks if a path is a two-cubic-Bézier circle of the expected bullet dimensions. Hollow
         * (stroke-only) bullets close their outline with an `h` op, which iText parses as an extra,
         * degenerate subpath — so segments are validated across all subpaths.
         */
        private boolean isBezierCircle(Path path) {
            List<IShape> segments = new ArrayList<>();
            for (Subpath subpath : path.getSubpaths()) {
                segments.addAll(subpath.getSegments());
            }
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

    // == Text and font extraction ========================================

    /** Extracts text with font information for all MCIDs on a page. */
    public static Map<Integer, McidContent> extractContentForPage(PdfPage page) {
        if (page == null) {
            return Map.of();
        }

        try {
            McidContentListener listener = new McidContentListener();
            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);
            return listener.buildResults();
        } catch (Exception e) {
            int pageNum = page.getDocument().getPageNumber(page);
            logger.debug("Failed to extract MCID content for page {}: {}", pageNum, e.getMessage());
        }

        return Map.of();
    }

    /** Extracts text for all MCIDs on a page (convenience wrapper). */
    public static Map<Integer, String> extractTextForPage(PdfPage page) {
        Map<Integer, McidContent> content = extractContentForPage(page);
        Map<Integer, String> textOnly = new HashMap<>();
        for (Map.Entry<Integer, McidContent> entry : content.entrySet()) {
            textOnly.put(entry.getKey(), entry.getValue().text());
        }
        return textOnly;
    }

    /** Gets the text content for all MCRs within a structure element. */
    public static String getTextForElement(PdfStructElem node, DocContext ctx, int pageNum) {
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
                    if (!combinedText.isEmpty()) combinedText.append(" ");
                    combinedText.append(text);
                }
            } else if (kid instanceof PdfStructElem childElem) {
                String childText = getTextForElement(childElem, mcidText);
                if (!childText.isEmpty()) {
                    if (!combinedText.isEmpty()) combinedText.append(" ");
                    combinedText.append(childText);
                }
            }
        }

        return combinedText.toString();
    }

    /** Collects text spans, font information, and content kinds for every MCID on a page. */
    private static class McidContentListener implements IEventListener {
        private final Map<Integer, SpanAccumulator> accumulators = new HashMap<>();

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo textInfo = (TextRenderInfo) data;
                int mcid = textInfo.getMcid();
                if (mcid >= 0) {
                    String text = textInfo.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        String fontName = extractFontName(textInfo);
                        float fontSize = effectiveFontSize(textInfo);
                        accumulators
                                .computeIfAbsent(mcid, k -> new SpanAccumulator())
                                .add(fontName, fontSize, text);
                    }
                }
            } else if (type == EventType.RENDER_IMAGE) {
                ImageRenderInfo imageInfo = (ImageRenderInfo) data;
                int mcid = imageInfo.getMcid();
                if (mcid >= 0) {
                    accumulators
                            .computeIfAbsent(mcid, k -> new SpanAccumulator())
                            .addKind(ContentKind.IMAGE);
                }
            } else if (type == EventType.RENDER_PATH) {
                PathRenderInfo pathInfo = (PathRenderInfo) data;
                int mcid = pathInfo.getMcid();
                if (mcid >= 0 && pathInfo.getOperation() != PathRenderInfo.NO_OP) {
                    accumulators
                            .computeIfAbsent(mcid, k -> new SpanAccumulator())
                            .addKind(ContentKind.PATH);
                }
            }
        }

        Map<Integer, McidContent> buildResults() {
            Map<Integer, McidContent> results = new HashMap<>();
            for (Map.Entry<Integer, SpanAccumulator> entry : accumulators.entrySet()) {
                McidContent content = entry.getValue().build();
                if (!content.text().isEmpty() || !content.kinds().isEmpty()) {
                    results.put(entry.getKey(), content);
                }
            }
            return results;
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_TEXT, EventType.RENDER_IMAGE, EventType.RENDER_PATH);
        }
    }

    /** Accumulates text spans, merging consecutive runs of the same font, and content kinds. */
    private static class SpanAccumulator {
        private static final float FONT_SIZE_TOLERANCE = 0.01f;
        private final Set<ContentKind> kinds = EnumSet.noneOf(ContentKind.class);
        private final List<TextSpan> completedSpans = new ArrayList<>();
        private String currentFontName;
        private float currentFontSize;
        private StringBuilder currentText = new StringBuilder();

        void add(String fontName, float fontSize, String text) {
            kinds.add(ContentKind.TEXT);
            boolean sameFont =
                    currentFontName != null
                            && currentFontName.equals(fontName)
                            && Math.abs(currentFontSize - fontSize) < FONT_SIZE_TOLERANCE;
            if (!sameFont) {
                flush();
                currentFontName = fontName;
                currentFontSize = fontSize;
            }

            if (!currentText.isEmpty()) {
                currentText.append(" ");
            }
            currentText.append(text);
        }

        void addKind(ContentKind kind) {
            kinds.add(kind);
        }

        private void flush() {
            if (!currentText.isEmpty() && currentFontName != null) {
                completedSpans.add(
                        new TextSpan(currentFontName, currentFontSize, currentText.toString()));
                currentText = new StringBuilder();
            }
        }

        McidContent build() {
            flush();
            StringBuilder combined = new StringBuilder();
            for (TextSpan span : completedSpans) {
                if (!combined.isEmpty()) {
                    combined.append(" ");
                }
                combined.append(span.text());
            }
            String cleaned = cleanExtractedText(combined.toString());
            return new McidContent(cleaned, List.copyOf(completedSpans), Set.copyOf(kinds));
        }
    }

    /** Extracts the PostScript font name from a text render event. */
    private static String extractFontName(TextRenderInfo info) {
        try {
            return info.getFont().getFontProgram().getFontNames().getFontName();
        } catch (NullPointerException e) {
            return "unknown";
        }
    }

    /**
     * Computes the effective rendered font size from the ascent-descent distance. This handles PDFs
     * where the Tf operator is set to 1pt and the text matrix scales to the actual size.
     */
    private static float effectiveFontSize(TextRenderInfo info) {
        LineSegment ascent = info.getAscentLine();
        LineSegment descent = info.getDescentLine();
        Vector ascentStart = ascent.getStartPoint();
        Vector descentStart = descent.getStartPoint();
        float dx = ascentStart.get(Vector.I1) - descentStart.get(Vector.I1);
        float dy = ascentStart.get(Vector.I2) - descentStart.get(Vector.I2);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Cleans extracted text by removing replacement characters and normalizing whitespace. */
    private static String cleanExtractedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove Unicode replacement character (U+FFFD)
        String cleaned = text.replace("\uFFFD", "");

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

        long singleCharWords = Arrays.stream(words).filter(w -> w.length() == 1).count();

        double ratio = (double) singleCharWords / words.length;
        return ratio > ARTIFICIAL_SPACING_RATIO;
    }

    /** Gets the bounding box for a single MCR by its MCID. */
    public static Rectangle getBoundsForMcid(DocContext ctx, int pageNum, int mcid) {
        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        pageNum, () -> extractBoundsForPage(ctx.doc().getPage(pageNum)));
        return mcidBounds.get(mcid);
    }

    /** Gets the union bounding box for all MCRs within a structure element. */
    public static Rectangle getBoundsForElement(PdfStructElem node, DocContext ctx, int pageNum) {
        Map<Integer, Rectangle> mcidBounds =
                ctx.getOrComputeMcidBounds(
                        pageNum, () -> extractBoundsForPage(ctx.doc().getPage(pageNum)));

        Rectangle result = null;
        for (PdfMcr mcr : StructTree.descendantsOf(node, PdfMcr.class)) {
            int mcid = mcr.getMcid();
            Rectangle bounds = mcidBounds.get(mcid);
            if (bounds != null) {
                result = Geometry.union(result, bounds);
            }
        }
        return result;
    }

    // == Bounds extraction ===============================================

    /** Extracts the bounding boxes for all MCIDs on a page. */
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
                int mcid = textInfo.getMcid();
                if (mcid >= 0) {
                    addBounds(bounds, mcid, rectFromText(textInfo));
                }
            } else if (type == EventType.RENDER_IMAGE) {
                ImageRenderInfo imageInfo = (ImageRenderInfo) data;
                int mcid = imageInfo.getMcid();
                if (mcid >= 0) {
                    addBounds(bounds, mcid, rectFromImage(imageInfo));
                }
            } else if (type == EventType.RENDER_PATH) {
                PathRenderInfo pathInfo = (PathRenderInfo) data;
                int mcid = pathInfo.getMcid();
                if (mcid >= 0 && pathInfo.getOperation() != PathRenderInfo.NO_OP) {
                    addBounds(bounds, mcid, rectFromPath(pathInfo));
                }
            }
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_TEXT, EventType.RENDER_IMAGE, EventType.RENDER_PATH);
        }
    }

    /** Adds a bounding box to the map of MCID bounds. */
    private static void addBounds(Map<Integer, Rectangle> bounds, int mcid, Rectangle rect) {
        if (rect == null) {
            return;
        }
        Rectangle existing = bounds.get(mcid);
        bounds.put(mcid, Geometry.union(existing, rect));
    }

    /** Computes the bounding box for a text render info. */
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

    /** Computes the bounding box for a path render info using CTM-transformed base points. */
    private static Rectangle rectFromPath(PathRenderInfo info) {
        Path path = info.getPath();
        if (path == null) {
            return null;
        }
        Matrix ctm = info.getCtm();
        List<Vector> transformed = new ArrayList<>();
        for (Subpath subpath : path.getSubpaths()) {
            Point start = subpath.getStartPoint();
            if (start != null) {
                transformed.add(
                        new Vector((float) start.getX(), (float) start.getY(), 1).cross(ctm));
            }
            for (IShape seg : subpath.getSegments()) {
                for (Point pt : seg.getBasePoints()) {
                    transformed.add(new Vector((float) pt.getX(), (float) pt.getY(), 1).cross(ctm));
                }
            }
        }
        return transformed.isEmpty() ? null : rectFromPoints(transformed.toArray(Vector[]::new));
    }

    /** Computes the bounding box for an image render info. */
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

    /** Computes the bounding box for a list of points. */
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
