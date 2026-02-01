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
package net.boyechko.pdf.autoa11y.core;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DocumentContext {
    private final PdfDocument doc;
    private final Map<Integer, Integer> objectToPageMapping;
    private final Map<Integer, Map<Integer, Rectangle>> mcidBoundsCache;
    private ProcessingService.ProcessingResult processingResult;

    public DocumentContext(PdfDocument doc) {
        this.doc = doc;
        this.objectToPageMapping = buildObjectToPageMapping(doc);
        this.mcidBoundsCache = new HashMap<>();
    }

    public PdfDocument doc() {
        return doc;
    }

    public void setProcessingResult(ProcessingService.ProcessingResult result) {
        this.processingResult = result;
    }

    public ProcessingService.ProcessingResult getProcessingResult() {
        return processingResult;
    }

    public int getPageNumber(int objectNumber) {
        return objectToPageMapping.getOrDefault(objectNumber, 0);
    }

    public Map<Integer, Rectangle> getOrComputeMcidBounds(
            int pageNum, Supplier<Map<Integer, Rectangle>> supplier) {
        return mcidBoundsCache.computeIfAbsent(pageNum, k -> supplier.get());
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
