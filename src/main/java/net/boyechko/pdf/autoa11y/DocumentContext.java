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
