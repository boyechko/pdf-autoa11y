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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Extracts text content associated with MCIDs (Marked Content IDs) from PDF documents. */
public final class McidTextExtractor {
    private static final Logger logger = LoggerFactory.getLogger(McidTextExtractor.class);
    private static final int MAX_DISPLAY_LENGTH = 30;
    private static final double ARTIFICIAL_SPACING_RATIO = 0.3;

    private static final Map<PdfDocument, Map<String, String>> mcidTextCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Extracts actual text content for a specific MCID from a PDF page. */
    public static String extractTextForMcid(PdfDocument document, int mcid, int pageNumber) {
        try {
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                logger.debug(
                        "Invalid page number {} for document with {} pages",
                        pageNumber,
                        document.getNumberOfPages());
                return "";
            }

            Map<String, String> docCache;
            synchronized (mcidTextCache) {
                docCache =
                        mcidTextCache.computeIfAbsent(document, doc -> new ConcurrentHashMap<>());
            }

            String cacheKey = pageNumber + ":" + mcid;
            String cached = docCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            PdfPage page = document.getPage(pageNumber);
            McidTextExtractionListener listener = new McidTextExtractionListener(mcid);

            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);

            String rawText = listener.getExtractedText();
            String cleanedText = cleanExtractedText(rawText);

            docCache.put(cacheKey, cleanedText);

            return cleanedText;
        } catch (Exception e) {
            logger.debug(
                    "Failed to extract text for MCID {} on page {}: {}",
                    mcid,
                    pageNumber,
                    e.getMessage());
            return "";
        }
    }

    /** Tracks marked content sections and extracts text for a specific MCID. */
    private static class McidTextExtractionListener implements IEventListener {
        private final int targetMcid;
        private final StringBuilder extractedText = new StringBuilder();

        public McidTextExtractionListener(int targetMcid) {
            this.targetMcid = targetMcid;
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo textInfo = (TextRenderInfo) data;

                // Get the current MCID from the graphics state
                Integer mcid = textInfo.getMcid();
                if (mcid != null && mcid == targetMcid) {
                    String text = textInfo.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        if (extractedText.length() > 0) {
                            extractedText.append(" ");
                        }
                        extractedText.append(text);
                    }
                }
            }
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return Set.of(EventType.RENDER_TEXT);
        }

        public String getExtractedText() {
            return extractedText.toString();
        }
    }

    /** Cleans extracted text by removing replacement characters and normalizing whitespace. */
    private static String cleanExtractedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleaned =
                text
                        // Remove Unicode replacement character (U+FFFD)
                        .replace("\uFFFD", "")
                        // Remove common replacement glyph
                        .replace("�", "");

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

    /** Gets the text content for all MCRs within a structure element. */
    public static String getMcrContent(PdfStructElem node, PdfDocument document, int pageNumber) {
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return "";

        List<PdfMcrNumber> mcrKids =
                kids.stream()
                        .filter(k -> k instanceof PdfMcrNumber)
                        .map(k -> (PdfMcrNumber) k)
                        .toList();

        if (mcrKids.isEmpty()) return "";

        if (mcrKids.size() == 1) {
            PdfMcrNumber mcr = mcrKids.get(0);
            int mcid = mcr.getMcid();
            String textContent = extractTextForMcid(document, mcid, pageNumber);

            if (textContent.isEmpty()) {
                return "";
            } else {
                return textContent;
            }
        } else {
            StringBuilder combinedText = new StringBuilder();
            for (PdfMcrNumber mcr : mcrKids) {
                String text = extractTextForMcid(document, mcr.getMcid(), pageNumber);
                if (!text.isEmpty()) {
                    if (combinedText.length() > 0) combinedText.append(" ");
                    combinedText.append(text);
                }
            }

            return combinedText.toString();
        }
    }

    /** Truncates text to a reasonable display length for validation output. */
    public static String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }

    public static String truncateText(String text) {
        return truncateText(text, MAX_DISPLAY_LENGTH);
    }
}
