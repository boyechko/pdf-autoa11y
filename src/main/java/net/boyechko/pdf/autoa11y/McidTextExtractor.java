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
package net.boyechko.pdf.autoa11y;

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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for extracting text content associated with MCIDs (Marked Content IDs) from PDF
 * documents. Follows the PDF specification for marked content sequences.
 *
 * <p>This class provides methods to: - Extract text content for specific MCIDs - Get text summaries
 * for structure elements containing MCRs - Handle multiple MCIDs within a single structure element
 */
public final class McidTextExtractor {
    private static final Logger logger = LoggerFactory.getLogger(McidTextExtractor.class);
    private static final int MAX_DISPLAY_LENGTH = 30;

    // Cache for MCID text extraction results
    private static final Map<String, String> mcidTextCache = new ConcurrentHashMap<>();

    /**
     * Extracts actual text content for a specific MCID from a PDF page.
     *
     * <p>This implementation parses the PDF content stream to find marked content sections and
     * correlates text operations with their containing marked content IDs.
     *
     * @param document The PDF document containing the content
     * @param mcid The Marked Content ID to extract text for
     * @param pageNumber The page number (1-based) containing the MCID
     * @return The actual text content associated with the MCID, or empty string if not found
     */
    public static String extractTextForMcid(PdfDocument document, int mcid, int pageNumber) {
        try {
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                logger.debug(
                        "Invalid page number {} for document with {} pages",
                        pageNumber,
                        document.getNumberOfPages());
                return "";
            }

            // Create cache key
            String cacheKey = pageNumber + ":" + mcid;
            if (mcidTextCache.containsKey(cacheKey)) {
                return mcidTextCache.get(cacheKey);
            }

            PdfPage page = document.getPage(pageNumber);
            McidTextExtractionListener listener = new McidTextExtractionListener(mcid);

            // Parse the content stream with our custom listener
            PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
            processor.processPageContent(page);

            String rawText = listener.getExtractedText();
            String cleanedText = cleanExtractedText(rawText);

            // Cache the result
            mcidTextCache.put(cacheKey, cleanedText);

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

    /**
     * Custom event listener that tracks marked content sections and extracts text for a specific
     * MCID.
     */
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

    /**
     * Cleans extracted text by removing replacement characters and normalizing whitespace. Handles
     * common issues from PDFs with missing or malformed font glyphs.
     *
     * @param text Raw text extracted from PDF content stream
     * @return Cleaned text with replacement characters removed and whitespace normalized
     */
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

    /**
     * Detects if text has artificial character-by-character spacing. This occurs when PDFs position
     * each character individually in the content stream.
     *
     * @param text Text to analyze
     * @return true if text appears to have artificial spacing between characters
     */
    private static boolean hasArtificialSpacing(String text) {
        String[] words = text.split("\\s+");
        if (words.length < 2) {
            return false; // Not enough data to determine
        }

        // Count single-character "words"
        long singleCharWords = Arrays.stream(words).filter(w -> w.length() == 1).count();

        // If more than 30% are single characters, assume artificial spacing
        double ratio = (double) singleCharWords / words.length;
        return ratio > 0.3;
    }

    /**
     * Gets a summary of text content for all MCRs within a structure element. This is useful for
     * validation output to show what actual content is associated with structure elements.
     *
     * @param node The structure element to analyze
     * @param document The PDF document containing the element
     * @param pageNumber The page number where the element appears
     * @return A formatted string describing the MCR content, or empty string if no MCRs
     */
    public static String getMcrContentSummary(
            PdfStructElem node, PdfDocument document, int pageNumber) {
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return "";

        List<PdfMcrNumber> mcrKids =
                kids.stream()
                        .filter(k -> k instanceof PdfMcrNumber)
                        .map(k -> (PdfMcrNumber) k)
                        .toList();

        if (mcrKids.isEmpty()) return "";

        if (mcrKids.size() == 1) {
            // Single MCR
            PdfMcrNumber mcr = mcrKids.get(0);
            int mcid = mcr.getMcid();
            String textContent = extractTextForMcid(document, mcid, pageNumber);

            if (textContent.isEmpty()) {
                return "";
            } else {
                return truncateText(textContent);
            }
        } else {
            // Multiple MCRs
            StringBuilder combinedText = new StringBuilder();
            for (PdfMcrNumber mcr : mcrKids) {
                String text = extractTextForMcid(document, mcr.getMcid(), pageNumber);
                if (!text.isEmpty()) {
                    if (combinedText.length() > 0) combinedText.append(" ");
                    combinedText.append(text);
                }
            }

            return truncateText(combinedText.toString());
        }
    }

    /** Truncates text to a reasonable display length for validation output. */
    private static String truncateText(String text) {
        if (text.length() <= MAX_DISPLAY_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_DISPLAY_LENGTH - 1) + "…";
    }
}
