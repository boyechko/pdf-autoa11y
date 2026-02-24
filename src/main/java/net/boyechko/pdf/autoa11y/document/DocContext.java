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
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Provides access to the document and its structure. */
public class DocContext {
    private final PdfDocument doc;
    private final Map<Integer, Integer> objectToPageMapping;
    private final Map<Integer, Map<Integer, Rectangle>> mcidBoundsCache;
    private final Map<Integer, Map<Integer, String>> mcidTextCache;
    private final Map<Integer, List<Content.BulletPosition>> bulletPositionCache;
    private final Map<Integer, Map<Integer, Set<Content.ContentKind>>> contentKindsCache;

    public DocContext(PdfDocument doc) {
        this.doc = doc;
        this.objectToPageMapping = buildObjectToPageMapping(doc);
        this.mcidBoundsCache = new HashMap<>();
        this.mcidTextCache = new HashMap<>();
        this.bulletPositionCache = new HashMap<>();
        this.contentKindsCache = new HashMap<>();
    }

    public PdfDocument doc() {
        return doc;
    }

    /** Returns the page number for a given object number based on the object-to-page mapping. */
    public int getPageNumber(int objectNumber) {
        return objectToPageMapping.getOrDefault(objectNumber, 0);
    }

    /** Returns the bounding box for a given MCID, extracting on first access. */
    public Map<Integer, Rectangle> getOrComputeMcidBounds(
            int pageNum, Supplier<Map<Integer, Rectangle>> supplier) {
        return mcidBoundsCache.computeIfAbsent(pageNum, k -> supplier.get());
    }

    /** Returns bullet positions for a page, extracting on first access. */
    public List<Content.BulletPosition> getOrComputeBulletPositions(
            int pageNum, Supplier<List<Content.BulletPosition>> supplier) {
        return bulletPositionCache.computeIfAbsent(pageNum, k -> supplier.get());
    }

    /** Returns all MCID text for a page, extracting on first access. */
    public Map<Integer, String> getMcidText(int pageNum) {
        return mcidTextCache.computeIfAbsent(
                pageNum, k -> Content.extractTextForPage(doc.getPage(pageNum)));
    }

    /**
     * Returns content kinds (text, image, or both) per MCID for a page, extracting on first access.
     */
    public Map<Integer, Set<Content.ContentKind>> getOrComputeContentKinds(
            int pageNum, Supplier<Map<Integer, Set<Content.ContentKind>>> supplier) {
        return contentKindsCache.computeIfAbsent(pageNum, k -> supplier.get());
    }

    /** Returns the text for a specific MCID, extracting the page on first access. */
    public String getMcidText(int pageNum, int mcid) {
        return getMcidText(pageNum).getOrDefault(mcid, "");
    }

    private Map<Integer, Integer> buildObjectToPageMapping(PdfDocument document) {
        Map<Integer, Integer> mapping = new HashMap<>();
        PdfStructTreeRoot root = document.getStructTreeRoot();

        if (root != null && root.getKids() != null) {
            // Start with the root's children, not the root itself
            for (IStructureNode child : root.getKids()) {
                buildMappingRecursive(child, document, mapping);
            }
        }

        return mapping;
    }

    /** Builds the object-to-page mapping recursively. */
    private void buildMappingRecursive(
            IStructureNode node, PdfDocument document, Map<Integer, Integer> mapping) {
        if (node instanceof PdfStructElem structElem) {
            // Process children first so their page info is available
            if (structElem.getKids() != null) {
                for (IStructureNode child : structElem.getKids()) {
                    buildMappingRecursive(child, document, mapping);
                }
            }

            // Get page number for this element (may inherit from children)
            int objNum = StructTree.objNum(structElem);
            int pageNum = getPageNumber(structElem, document);
            if (pageNum > 0) {
                mapping.put(objNum, pageNum);
            }
        }
    }

    /**
     * Returns the page number for a structure element, using the /Pg dictionary or indirect
     * reference.
     */
    private int getPageNumber(PdfStructElem node, PdfDocument document) {
        PdfDictionary dict = node.getPdfObject();
        PdfDictionary pg = dict.getAsDictionary(PdfName.Pg);

        if (pg != null) {
            return document.getPageNumber(pg);
        }

        // If this element doesn't have a page reference, check its children
        if (node.getKids() != null) {
            for (IStructureNode child : node.getKids()) {
                if (child instanceof PdfStructElem childElem) {
                    int childPage = getPageNumber(childElem, document);
                    if (childPage > 0) {
                        return childPage;
                    }
                }
            }
        }

        return 0;
    }
}
