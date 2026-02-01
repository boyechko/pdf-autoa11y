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
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a Link structure element for a Link annotation and associates them via an OBJR. The Link
 * element is added under the Part element for the page (created by SetupDocumentStructure).
 */
public class CreateLinkTag implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(CreateLinkTag.class);
    // After SetupDocumentStructure (10), so Parts exist
    private static final int P_CREATE_LINK = 22;

    private final PdfDictionary annotDict;
    private final int pageNum;

    public CreateLinkTag(PdfDictionary annotDict, int pageNum) {
        this.annotDict = annotDict;
        this.pageNum = pageNum;
    }

    @Override
    public int priority() {
        return P_CREATE_LINK;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            logger.warn("No structure tree root, cannot create Link tag");
            return;
        }

        // Find the Document element
        PdfStructElem documentElem = findDocumentElement(root);
        if (documentElem == null) {
            logger.warn("No Document element found, cannot create Link tag");
            return;
        }

        // Find the annotation object on the page
        PdfPage page = ctx.doc().getPage(pageNum);
        PdfAnnotation annotation = findMatchingAnnotation(page, annotDict);
        if (annotation == null) {
            logger.warn("Could not find annotation on page {}", pageNum);
            return;
        }

        // Find the Part element for this page (created by SetupDocumentStructure)
        PdfStructElem partElem = SetupDocumentStructure.findPartForPage(documentElem, page);
        if (partElem == null) {
            logger.warn("No Part element found for page {}, cannot create Link tag", pageNum);
            return;
        }

        // Create Link structure element under the Part with /Pg set (needed for OBJR validity)
        PdfStructElem linkElem = new PdfStructElem(ctx.doc(), PdfName.Link, page);
        partElem.addKid(linkElem);

        // Create OBJR to connect the annotation to the Link structure element
        int structParentIndex = ctx.doc().getNextStructParentIndex();
        PdfObjRef objRef = new PdfObjRef(annotation, linkElem, structParentIndex);
        linkElem.addKid(objRef);

        int annotObjNum =
                annotDict.getIndirectReference() != null
                        ? annotDict.getIndirectReference().getObjNumber()
                        : 0;
        logger.debug(
                "Created Link tag for annotation obj #{} on page {} with StructParent {}",
                annotObjNum,
                pageNum,
                structParentIndex);
    }

    /** Finds the Document element in the structure tree. */
    private PdfStructElem findDocumentElement(PdfStructTreeRoot root) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return null;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                PdfName role = elem.getRole();
                if (role != null && "Document".equals(role.getValue())) {
                    return elem;
                }
            }
        }
        return null;
    }

    /** Finds the annotation on the page that matches our stored dictionary. */
    private PdfAnnotation findMatchingAnnotation(PdfPage page, PdfDictionary targetDict) {
        for (PdfAnnotation annot : page.getAnnotations()) {
            PdfDictionary annotPdfObj = annot.getPdfObject();

            // Check by same instance or indirect reference
            if (annotPdfObj == targetDict) {
                return annot;
            }
            if (targetDict.getIndirectReference() != null
                    && targetDict
                            .getIndirectReference()
                            .equals(annotPdfObj.getIndirectReference())) {
                return annot;
            }
        }
        return null;
    }

    @Override
    public String describe() {
        int annotObjNum =
                annotDict.getIndirectReference() != null
                        ? annotDict.getIndirectReference().getObjNumber()
                        : 0;
        return "Created Link tag for annotation #" + annotObjNum + " on page " + pageNum;
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
