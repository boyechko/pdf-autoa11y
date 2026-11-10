// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-page marked-content facts, each extracted from the page on first access. */
public class PageContent {
    private final PdfDocument doc;
    private final Map<Integer, Map<Integer, Rectangle>> boundsCache = new HashMap<>();
    private final Map<Integer, Map<Integer, List<Rectangle>>> lineBoundsCache = new HashMap<>();
    private final Map<Integer, Map<Integer, List<Content.TextLine>>> textLinesCache =
            new HashMap<>();
    private final Map<Integer, Map<Integer, Content.McidContent>> contentCache = new HashMap<>();
    private final Map<Integer, Map<Integer, String>> textCache = new HashMap<>();
    private final Map<Integer, List<Content.BulletPosition>> bulletCache = new HashMap<>();

    public PageContent(PdfDocument doc) {
        this.doc = doc;
    }

    /** Returns each MCID's bounding box on a page. */
    public Map<Integer, Rectangle> bounds(int pageNum) {
        return boundsCache.computeIfAbsent(
                pageNum, k -> Content.extractBoundsForPage(doc.getPage(pageNum)));
    }

    /** Returns an MCR's bounding box, or null when it has none or its page is unresolved. */
    public Rectangle bounds(PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        return pageNum > 0 ? bounds(pageNum).get(mcr.getMcid()) : null;
    }

    /**
     * Returns the union bounding box of an element's MCRs on one page, or null when none has one.
     */
    public Rectangle bounds(PdfStructElem node, int pageNum) {
        Map<Integer, Rectangle> mcidBounds = bounds(pageNum);
        Rectangle result = null;
        for (PdfMcr mcr : StructTree.descendantsOf(node, PdfMcr.class)) {
            if (StructTree.pageOf(mcr) != pageNum) {
                continue;
            }
            Rectangle bounds = mcidBounds.get(mcr.getMcid());
            if (bounds != null) {
                result = Geometry.union(result, bounds);
            }
        }
        return result;
    }

    /** Returns each MCID's text lines on a page, top to bottom. */
    public Map<Integer, List<Rectangle>> lineBounds(int pageNum) {
        return lineBoundsCache.computeIfAbsent(
                pageNum,
                k -> {
                    Map<Integer, List<Rectangle>> bounds = new HashMap<>();
                    textLines(pageNum)
                            .forEach(
                                    (mcid, lines) ->
                                            bounds.put(
                                                    mcid,
                                                    lines.stream()
                                                            .map(Content.TextLine::bounds)
                                                            .toList()));
                    return bounds;
                });
    }

    /** Returns an MCR's text lines, top to bottom, or none when its page is unresolved. */
    public List<Rectangle> lineBounds(PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        return pageNum > 0 ? lineBounds(pageNum).getOrDefault(mcr.getMcid(), List.of()) : List.of();
    }

    /** Returns each MCID's text lines on a page, top to bottom, with word measurements. */
    public Map<Integer, List<Content.TextLine>> textLines(int pageNum) {
        return textLinesCache.computeIfAbsent(
                pageNum, k -> Content.extractTextLinesForPage(doc.getPage(pageNum)));
    }

    /** Returns an MCR's text lines with word measurements, or none when its page is unresolved. */
    public List<Content.TextLine> textLines(PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        return pageNum > 0 ? textLines(pageNum).getOrDefault(mcr.getMcid(), List.of()) : List.of();
    }

    /** Returns text with font information for all MCIDs on a page. */
    public Map<Integer, Content.McidContent> content(int pageNum) {
        return contentCache.computeIfAbsent(
                pageNum, k -> Content.extractContentForPage(doc.getPage(pageNum)));
    }

    /** Returns an MCR's text and font information, or null when it has none. */
    public Content.McidContent content(PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        return pageNum > 0 ? content(pageNum).get(mcr.getMcid()) : null;
    }

    /** Returns all MCID text on a page. */
    public Map<Integer, String> text(int pageNum) {
        return textCache.computeIfAbsent(
                pageNum,
                k -> {
                    Map<Integer, String> text = new HashMap<>();
                    content(pageNum).forEach((mcid, mc) -> text.put(mcid, mc.text()));
                    return text;
                });
    }

    /** Returns the text for a specific MCID, or an empty string if it has none. */
    public String text(int pageNum, int mcid) {
        return text(pageNum).getOrDefault(mcid, "");
    }

    /** Returns the bullet glyphs drawn on a page. */
    public List<Content.BulletPosition> bullets(int pageNum) {
        return bulletCache.computeIfAbsent(
                pageNum, k -> Content.extractBulletPositionsForPage(doc.getPage(pageNum)));
    }
}
