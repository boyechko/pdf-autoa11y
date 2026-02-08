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
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flattens needless Part/Sect/Div wrappers by promoting their children to the parent element. */
public class FlattenNesting implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(FlattenNesting.class);
    private static final int P_STRUCTURE = 15; // After document setup, before other structure fixes

    private final List<PdfStructElem> elementsToFlatten;
    private int flattened = 0;

    public FlattenNesting(List<PdfStructElem> elementsToFlatten) {
        this.elementsToFlatten = new ArrayList<>(elementsToFlatten);
    }

    @Override
    public int priority() {
        return P_STRUCTURE;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        // Process in reverse order to handle nested wrappers correctly
        for (int i = elementsToFlatten.size() - 1; i >= 0; i--) {
            PdfStructElem wrapper = elementsToFlatten.get(i);
            flattenElement(wrapper);
        }
    }

    private void flattenElement(PdfStructElem wrapper) {
        IStructureNode parentNode = wrapper.getParent();

        List<PdfStructElem> childrenToMove = collectStructElemChildren(wrapper);
        if (childrenToMove.isEmpty()) {
            StructureTree.removeFromParent(wrapper, parentNode);
            flattened++;
            return;
        }

        PdfArray kArray = StructureTree.getKArray(parentNode);
        if (kArray == null) {
            logger.debug("Parent K array is null, cannot flatten");
            return;
        }

        int wrapperIndex = StructureTree.findIndexInKArray(kArray, wrapper);
        if (wrapperIndex < 0) {
            logger.debug("Could not find wrapper in parent K array");
            return;
        }

        promoteChildren(wrapper, childrenToMove, kArray, wrapperIndex, parentNode);

        // Remove the wrapper (now shifted past the inserted children)
        kArray.remove(wrapperIndex + childrenToMove.size());

        flattened++;
        logger.debug(
                "Flattened {} with {} children at position {}",
                wrapper.getRole().getValue(),
                childrenToMove.size(),
                wrapperIndex);
    }

    private List<PdfStructElem> collectStructElemChildren(PdfStructElem elem) {
        List<PdfStructElem> children = new ArrayList<>();
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return children;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem childElem) {
                children.add(childElem);
            }
        }
        return children;
    }

    private void promoteChildren(
            PdfStructElem wrapper,
            List<PdfStructElem> children,
            PdfArray kArray,
            int wrapperIndex,
            IStructureNode newParent) {
        PdfDictionary newParentDict = StructureTree.getPdfObject(newParent);
        for (int i = 0; i < children.size(); i++) {
            PdfStructElem child = children.get(i);
            wrapper.removeKid(child);
            child.getPdfObject().put(PdfName.P, newParentDict);
            kArray.add(wrapperIndex + i, child.getPdfObject());
        }
    }

    @Override
    public String describe() {
        return "Flattened " + flattened + " grouping element(s)";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
