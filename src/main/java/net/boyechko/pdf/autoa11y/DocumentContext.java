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

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.HashMap;
import java.util.Map;

public class DocumentContext {
    private final PdfDocument doc;
    private final Map<Integer, Integer> objectToPageMapping;

    public DocumentContext(PdfDocument doc) {
        this.doc = doc;
        this.objectToPageMapping = buildObjectToPageMapping(doc);
    }

    public PdfDocument doc() {
        return doc;
    }

    /**
     * Gets the page number for a given object number.
     *
     * @param objectNumber The PDF object number
     * @return The page number (1-based), or 0 if not found
     */
    public int getPageNumber(int objectNumber) {
        return objectToPageMapping.getOrDefault(objectNumber, 0);
    }

    /**
     * Builds a mapping from object number to page number for all structure elements in the
     * document.
     */
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

    private void buildMappingRecursive(
            IStructureNode node, PdfDocument document, Map<Integer, Integer> mapping) {
        if (node instanceof PdfStructElem structElem) {
            // Process children first so their page info is available
            if (structElem.getKids() != null) {
                for (IStructureNode child : structElem.getKids()) {
                    buildMappingRecursive(child, document, mapping);
                }
            }

            // Now get page number for this element (may inherit from children)
            int objNum = structElem.getPdfObject().getIndirectReference().getObjNumber();
            int pageNum = getPageNumber(structElem, document);
            if (pageNum > 0) {
                mapping.put(objNum, pageNum);
            }
        }
    }

    /**
     * Gets the page number for a specific structure element. If the element doesn't have a direct
     * page reference, checks its children recursively.
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
