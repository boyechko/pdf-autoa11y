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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up proper document structure: creates Document wrapper if missing, then organizes content
 * into Part-per-page layout. This ensures the structure tree follows the pattern:
 *
 * <pre>
 * StructTreeRoot
 * └── Document
 *     ├── Part (page 1)
 *     │   └── content...
 *     ├── Part (page 2)
 *     │   └── content...
 *     └── ...
 * </pre>
 */
public class SetupDocumentStructure implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(SetupDocumentStructure.class);
    // After FlattenNesting (15), before CreateLinkTag (22)
    private static final int P_DOC_SETUP = 18;

    private int elementsWrapped = 0;
    private int partsCreated = 0;
    private int elementsMoved = 0;

    @Override
    public int priority() {
        return P_DOC_SETUP;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }

        PdfStructElem documentElem = ensureDocumentWrapper(ctx, root);
        if (documentElem == null) {
            return;
        }

        organizeByPage(ctx, documentElem);
    }

    private PdfStructElem ensureDocumentWrapper(DocumentContext ctx, PdfStructTreeRoot root) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null || kids.isEmpty()) {
            return root.addKid(new PdfStructElem(ctx.doc(), PdfName.Document));
        }

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                if (elem.getRole() == PdfName.Document) {
                    return elem;
                }
            }
        }

        List<PdfStructElem> elementsToMove = new ArrayList<>();
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                elementsToMove.add(elem);
            }
        }

        PdfStructElem document = root.addKid(new PdfStructElem(ctx.doc(), PdfName.Document));

        PdfDictionary rootDict = root.getPdfObject();
        PdfObject kObj = rootDict.get(PdfName.K);

        if (kObj instanceof PdfArray kArray) {
            // Remove all elements except Document from the K array
            List<PdfObject> toRemove = new ArrayList<>();
            for (int i = 0; i < kArray.size(); i++) {
                PdfObject obj = kArray.get(i);
                if (obj != document.getPdfObject()
                        && !obj.equals(document.getPdfObject().getIndirectReference())) {
                    toRemove.add(obj);
                }
            }
            for (PdfObject obj : toRemove) {
                kArray.remove(obj);
            }
        }

        for (PdfStructElem elem : elementsToMove) {
            document.addKid(elem);
        }

        elementsWrapped = elementsToMove.size();
        logger.debug("Wrapped {} element(s) in Document", elementsWrapped);

        return document;
    }

    /** Creates Parts for all pages and moves content to appropriate Parts. */
    private void organizeByPage(DocumentContext ctx, PdfStructElem documentElem) {
        Map<Integer, PdfStructElem> pageParts = createPartsForAllPages(ctx, documentElem);
        moveContentToParts(ctx, documentElem, pageParts);
    }

    private Map<Integer, PdfStructElem> createPartsForAllPages(
            DocumentContext ctx, PdfStructElem documentElem) {
        Map<Integer, PdfStructElem> pageParts = new HashMap<>();
        PdfDocument doc = ctx.doc();

        for (int pageNum = 1; pageNum <= doc.getNumberOfPages(); pageNum++) {
            PdfPage page = doc.getPage(pageNum);
            PdfStructElem partElem = findPartForPage(documentElem, page);

            if (partElem == null) {
                partElem = new PdfStructElem(doc, PdfName.Part);
                partElem.getPdfObject().put(PdfName.Pg, page.getPdfObject());
                documentElem.addKid(partElem);
                partsCreated++;
                logger.debug(
                        "Created Part element obj #{} for page {}",
                        partElem.getPdfObject().getIndirectReference().getObjNumber(),
                        pageNum);
            }

            pageParts.put(pageNum, partElem);
        }

        return pageParts;
    }

    private void moveContentToParts(
            DocumentContext ctx,
            PdfStructElem documentElem,
            Map<Integer, PdfStructElem> pageParts) {

        // Collect elements to move (can't modify while iterating)
        List<PdfStructElem> elementsToMove = new ArrayList<>();
        List<IStructureNode> kids = documentElem.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                PdfName role = elem.getRole();
                // Skip Part elements (they're our containers)
                if (role == PdfName.Part) {
                    continue;
                }
                elementsToMove.add(elem);
            }
        }

        // Move each element to its page's Part
        for (PdfStructElem elem : elementsToMove) {
            int pageNum = determinePageNumber(ctx, elem);
            if (pageNum > 0 && pageParts.containsKey(pageNum)) {
                PdfStructElem targetPart = pageParts.get(pageNum);
                moveElementToPart(documentElem, elem, targetPart);
                elementsMoved++;
            } else {
                logger.debug(
                        "Element {} has no page association, leaving under Document",
                        elem.getRole() != null ? elem.getRole().getValue() : "unknown");
            }
        }
    }

    /** Determines which page an element belongs to based on /Pg or descendant /Pg. */
    private int determinePageNumber(DocumentContext ctx, PdfStructElem elem) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg != null) {
            int pageNum = ctx.doc().getPageNumber(pg);
            if (pageNum > 0) return pageNum;
        }

        // Check via indirect reference in context cache
        if (elem.getPdfObject().getIndirectReference() != null) {
            int objNum = elem.getPdfObject().getIndirectReference().getObjNumber();
            int pageNum = ctx.getPageNumber(objNum);
            if (pageNum > 0) return pageNum;
        }

        // Recursively check first child with a page
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    int pageNum = determinePageNumber(ctx, childElem);
                    if (pageNum > 0) return pageNum;
                }
            }
        }

        return 0;
    }

    private void moveElementToPart(
            PdfStructElem documentElem, PdfStructElem elem, PdfStructElem targetPart) {
        PdfArray docKids = documentElem.getPdfObject().getAsArray(PdfName.K);
        if (docKids == null) return;

        // Remove from Document's K array
        PdfObject elemObj = elem.getPdfObject();
        boolean removed = false;
        for (int i = 0; i < docKids.size(); i++) {
            PdfObject obj = docKids.get(i);
            if (obj == elemObj
                    || (elemObj.getIndirectReference() != null
                            && elemObj.getIndirectReference().equals(obj))) {
                docKids.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            // Update parent reference (/P) and add to Part
            elem.getPdfObject().put(PdfName.P, targetPart.getPdfObject());
            targetPart.addKid(elem);
            logger.debug(
                    "Moved {} to Part obj #{}",
                    elem.getRole() != null ? elem.getRole().getValue() : "unknown",
                    targetPart.getPdfObject().getIndirectReference().getObjNumber());
        }
    }

    public static PdfStructElem findPartForPage(PdfStructElem documentElem, PdfPage page) {
        PdfDictionary pageDict = page.getPdfObject();
        List<IStructureNode> kids = documentElem.getKids();
        if (kids == null) return null;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                PdfName role = elem.getRole();
                if (role == PdfName.Part) {
                    PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
                    if (pg != null) {
                        if (pg.equals(pageDict)) {
                            return elem;
                        }
                        if (pageDict.getIndirectReference() != null
                                && pageDict.getIndirectReference()
                                        .equals(pg.getIndirectReference())) {
                            return elem;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("Set up document structure");
        if (elementsWrapped > 0) {
            sb.append(": wrapped ").append(elementsWrapped).append(" element(s) in Document");
        }
        if (partsCreated > 0) {
            if (elementsWrapped > 0) sb.append(", ");
            else sb.append(": ");
            sb.append("created ").append(partsCreated).append(" Part(s)");
        }
        if (elementsMoved > 0) {
            sb.append(", moved ").append(elementsMoved).append(" element(s)");
        }
        return sb.toString();
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
