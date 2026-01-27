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
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;

/**
 * Wraps all root-level structure elements in a Document element. This fix ensures the structure
 * tree has the proper Document wrapper required by PDF/UA-1.
 */
public class WrapInDocument implements IssueFix {
    private static final int P_DOC_SETUP = 10;
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

        List<IStructureNode> kids = root.getKids();
        if (kids == null || kids.isEmpty()) {
            return;
        }

        // Check if already has Document wrapper (idempotent)
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                if ("Document".equals(elem.getRole().getValue())) {
                    return;
                }
            }
        }

        // Collect all existing kids that need to be moved
        List<PdfStructElem> elementsToMove = new ArrayList<>();
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                elementsToMove.add(elem);
            }
        }

        // Create new Document element and add it to root
        PdfStructElem document = root.addKid(new PdfStructElem(ctx.doc(), PdfName.Document));

        // Move each element under Document by manipulating the underlying PDF objects
        // Since PdfStructTreeRoot doesn't have removeKid, we work with the K array directly
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

        // Add elements to Document
        for (PdfStructElem elem : elementsToMove) {
            document.addKid(elem);
        }

        elementsWrapped = elementsToMove.size();
    }

    @Override
    public String describe() {
        return "Wrapped " + elementsWrapped + " root element(s) in Document";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
