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

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Organizes direct Document children into page-level Part containers. */
public class NormalizePageParts implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(NormalizePageParts.class);
    // After SetupDocumentStructure (18), before link creation (22)
    private static final int P_PAGE_PARTS = 19;

    private int partsCreated = 0;
    private int elementsMoved = 0;

    @Override
    public int priority() {
        return P_PAGE_PARTS;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        if (ctx.doc().getNumberOfPages() < 2) {
            return;
        }

        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return;
        }

        PdfStructElem documentElem = StructureTree.findFirstChild(root, PdfName.Document);
        if (documentElem == null) {
            return;
        }

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
                partElem = new PdfStructElem(doc, PdfName.Part, page);
                partElem.getPdfObject().put(PdfName.Pg, page.getPdfObject());
                partElem.put(PdfName.T, new PdfString("p. " + pageNum));
                documentElem.addKid(partElem);
                partsCreated++;
                logger.debug(
                        "Created Part element obj. #{} for page {}",
                        StructureTree.objNum(partElem),
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
        List<PdfStructElem> elementsToMove = new ArrayList<>();
        List<IStructureNode> kids = documentElem.getKids();
        if (kids == null) {
            return;
        }

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem && !PdfName.Part.equals(elem.getRole())) {
                elementsToMove.add(elem);
            }
        }

        for (PdfStructElem elem : elementsToMove) {
            int pageNum = StructureTree.determinePageNumber(ctx, elem);
            if (pageNum > 0 && pageParts.containsKey(pageNum)) {
                PdfStructElem targetPart = pageParts.get(pageNum);
                if (StructureTree.moveElement(documentElem, elem, targetPart)) {
                    elementsMoved++;
                    logger.debug(
                            "Moved {} (obj. #{}) into Part for page {}",
                            elem.getRole() != null ? elem.getRole().getValue() : "unknown",
                            StructureTree.objNum(elem),
                            pageNum);
                }
            } else {
                logger.debug(
                        "Element {} has no page association, leaving under Document",
                        elem.getRole() != null ? elem.getRole().getValue() : "unknown");
            }
        }
    }

    public static PdfStructElem findPartForPage(PdfStructElem documentElem, PdfPage page) {
        List<IStructureNode> kids = documentElem.getKids();
        if (kids == null) {
            return null;
        }

        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfStructElem elem) || !PdfName.Part.equals(elem.getRole())) {
                continue;
            }

            PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
            if (pg != null && StructureTree.isSamePage(pg, page)) {
                return elem;
            }
        }
        return null;
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("Normalized page Parts");
        if (partsCreated > 0 || elementsMoved > 0) {
            sb.append(": ");
            if (partsCreated > 0) {
                sb.append("created ").append(partsCreated).append(" Part(s)");
            }
            if (partsCreated > 0 && elementsMoved > 0) {
                sb.append(", ");
            }
            if (elementsMoved > 0) {
                sb.append("moved ").append(elementsMoved).append(" element(s)");
            }
        }
        return sb.toString();
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }

    @Override
    public String groupLabel() {
        return "page-level Part elements created";
    }
}
