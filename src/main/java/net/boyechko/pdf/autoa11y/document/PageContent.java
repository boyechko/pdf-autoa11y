// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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

    /**
     * Returns an element's text lines on one page, in reading order, one rectangle per line. A line
     * broken up into multiple MCRs still returns a single bounding rectangle.
     */
    public List<Rectangle> lineBounds(PdfStructElem node, int pageNum) {
        Map<Integer, List<Rectangle>> mcidLines = lineBounds(pageNum);
        List<Rectangle> lines = new ArrayList<>();
        for (PdfMcr mcr : StructTree.descendantsOf(node, PdfMcr.class)) {
            if (StructTree.pageOf(mcr) == pageNum) {
                mcidLines
                        .getOrDefault(mcr.getMcid(), List.<Rectangle>of())
                        .forEach(fragment -> mergeIntoLines(lines, fragment));
            }
        }
        return lines;
    }

    /**
     * Checks whether the MCR's first line sits on the previous MCR's last line, as when a dissolved
     * inline span leaves a line's tail in a block of its own.
     */
    public boolean continuesLineOf(PdfMcr previous, PdfMcr mcr) {
        if (previous == null || StructTree.pageOf(previous) != StructTree.pageOf(mcr)) {
            return false;
        }
        List<Rectangle> prevLines = lineBounds(previous);
        List<Rectangle> lines = lineBounds(mcr);
        return !prevLines.isEmpty()
                && !lines.isEmpty()
                && Content.onSameLine(prevLines.get(prevLines.size() - 1), lines.get(0));
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

    /**
     * Returns an element's text lines with word measurements, in reading order across pages,
     * joining an MCR's first line to the previous MCR's last when they share a line.
     */
    public List<Content.TextLine> textLines(PdfStructElem node) {
        List<Content.TextLine> lines = new ArrayList<>();
        PdfMcr previous = null;
        for (PdfMcr mcr : StructTree.descendantsOf(node, PdfMcr.class)) {
            List<Content.TextLine> mcrLines = textLines(mcr);
            int first = 0;
            if (!lines.isEmpty() && !mcrLines.isEmpty() && continuesLineOf(previous, mcr)) {
                Content.TextLine head = lines.removeLast();
                Content.TextLine tail = mcrLines.get(0);
                lines.add(
                        new Content.TextLine(
                                Geometry.union(head.bounds(), tail.bounds()),
                                head.firstWordWidth(),
                                head.spaceWidth(),
                                tail.lastChar()));
                first = 1;
            }
            lines.addAll(mcrLines.subList(first, mcrLines.size()));
            previous = mcr;
        }
        return lines;
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

    /**
     * Returns the content of each MCR in an element and its descendants that holds any text, image,
     * or path, keyed by page-scoped MCID.
     */
    public Map<Content.PageMcid, Content.McidContent> content(PdfStructElem elem) {
        Map<Content.PageMcid, Content.McidContent> results = new LinkedHashMap<>();
        if (elem == null) {
            return results;
        }
        for (PdfMcr mcr : StructTree.descendantsOf(elem, PdfMcr.class)) {
            int pageNum = StructTree.pageOf(mcr);
            if (pageNum <= 0) {
                continue;
            }
            Content.McidContent mc = content(pageNum).get(mcr.getMcid());
            if (mc != null && !mc.kinds().isEmpty()) {
                results.put(new Content.PageMcid(pageNum, mcr.getMcid()), mc);
            }
        }
        return results;
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

    /**
     * Returns the text of an element's MCRs on one page, recursing into children. Only bare-number
     * MCRs are read, since they resolve to the page through their parent; MCR dictionaries are
     * skipped.
     */
    public String text(PdfStructElem node, int pageNum) {
        return textOf(node, text(pageNum));
    }

    /**
     * Returns the text of all an element's MCRs, reading each from its own page, so an element
     * continued across a page break yields all of its text.
     */
    public String text(PdfStructElem node) {
        StringJoiner combinedText = new StringJoiner(" ");
        for (PdfMcr mcr : StructTree.descendantsOf(node, PdfMcr.class)) {
            int pageNum = StructTree.pageOf(mcr);
            String text = pageNum > 0 ? text(pageNum, mcr.getMcid()) : "";
            if (!text.isEmpty()) {
                combinedText.add(text);
            }
        }
        return combinedText.toString();
    }

    /** Returns the bullet glyphs drawn on a page. */
    public List<Content.BulletPosition> bullets(int pageNum) {
        return bulletCache.computeIfAbsent(
                pageNum, k -> Content.extractBulletPositionsForPage(doc.getPage(pageNum)));
    }

    /** Adds a fragment to the line it sits on, widening that line, or starts a new line with it. */
    private static void mergeIntoLines(List<Rectangle> lines, Rectangle fragment) {
        for (int i = 0; i < lines.size(); i++) {
            if (Content.onSameLine(lines.get(i), fragment)) {
                lines.set(i, Geometry.union(lines.get(i), fragment));
                return;
            }
        }
        lines.add(new Rectangle(fragment));
    }

    /** Joins the text of an element's bare-number MCRs from one page's MCID text, depth first. */
    private static String textOf(PdfStructElem node, Map<Integer, String> mcidText) {
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
                String childText = textOf(childElem, mcidText);
                if (!childText.isEmpty()) {
                    if (!combinedText.isEmpty()) combinedText.append(" ");
                    combinedText.append(childText);
                }
            }
        }

        return combinedText.toString();
    }
}
