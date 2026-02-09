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
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ensures the structure tree has a top-level Document element. */
public class SetupDocumentStructure implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(SetupDocumentStructure.class);
    // After FlattenNesting (15)
    private static final int P_DOC_SETUP = 18;

    private int elementsWrapped = 0;

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

        ensureDocumentWrapper(ctx, root);
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

    // TODO: It would be nice if this could use onDetail()
    @Override
    public String describe() {
        if (elementsWrapped > 0) {
            return "Set up document structure: wrapped "
                    + elementsWrapped
                    + " element(s) in Document";
        }
        return "Set up document structure";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
