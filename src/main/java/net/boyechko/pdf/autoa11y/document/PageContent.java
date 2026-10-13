// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-page marked-content facts, each extracted from the page on first access. */
public class PageContent {
    private final PdfDocument doc;
    private final Map<Integer, Map<Integer, Rectangle>> boundsCache = new HashMap<>();
    private final Map<Integer, Map<Integer, List<Rectangle>>> lineBoundsCache = new HashMap<>();
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

    /** Returns each MCID's text lines on a page, top to bottom. */
    public Map<Integer, List<Rectangle>> lineBounds(int pageNum) {
        return lineBoundsCache.computeIfAbsent(
                pageNum, k -> Content.extractLineBoundsForPage(doc.getPage(pageNum)));
    }

    /** Returns text with font information for all MCIDs on a page. */
    public Map<Integer, Content.McidContent> content(int pageNum) {
        return contentCache.computeIfAbsent(
                pageNum, k -> Content.extractContentForPage(doc.getPage(pageNum)));
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
