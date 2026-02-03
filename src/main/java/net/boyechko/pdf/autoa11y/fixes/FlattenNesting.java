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
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
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

    // TODO: Split into smaller methods
    private void flattenElement(PdfStructElem wrapper) {
        IStructureNode parentNode = wrapper.getParent();

        List<IStructureNode> wrapperKids = wrapper.getKids();
        if (wrapperKids == null || wrapperKids.isEmpty()) {
            removeFromParent(wrapper, parentNode);
            flattened++;
            return;
        }

        List<PdfStructElem> childrenToMove = new ArrayList<>();
        for (IStructureNode kid : wrapperKids) {
            if (kid instanceof PdfStructElem childElem) {
                childrenToMove.add(childElem);
            }
        }

        if (parentNode instanceof PdfStructElem parent) {
            // Find wrapper's position in parent's children to maintain order
            PdfArray kArray = parent.getPdfObject().getAsArray(PdfName.K);
            if (kArray == null) {
                logger.debug("Parent K array is null, cannot flatten");
                return;
            }

            int wrapperIndex = -1;
            PdfObject wrapperObj = wrapper.getPdfObject();
            for (int i = 0; i < kArray.size(); i++) {
                PdfObject obj = kArray.get(i);
                if (obj == wrapperObj || obj.equals(wrapperObj.getIndirectReference())) {
                    wrapperIndex = i;
                    break;
                }
            }

            if (wrapperIndex < 0) {
                logger.debug("Could not find wrapper in parent K array");
                return;
            }

            // Insert children at wrapper's position (before removing wrapper)
            for (int i = 0; i < childrenToMove.size(); i++) {
                PdfStructElem child = childrenToMove.get(i);
                wrapper.removeKid(child);
                child.getPdfObject().put(PdfName.P, parent.getPdfObject());
                // Insert at position (each insertion shifts subsequent items)
                kArray.add(wrapperIndex + i, child.getPdfObject());
            }

            // Now remove the wrapper
            kArray.remove(wrapperIndex + childrenToMove.size());

            flattened++;
            logger.debug(
                    "Flattened {} with {} children into parent {} at position {}",
                    wrapper.getRole().getValue(),
                    childrenToMove.size(),
                    parent.getRole().getValue(),
                    wrapperIndex);
        } else if (parentNode instanceof PdfStructTreeRoot root) {
            PdfArray kArray = root.getPdfObject().getAsArray(PdfName.K);
            if (kArray == null) {
                logger.debug("Root K array is null, cannot flatten");
                return;
            }

            int wrapperIndex = -1;
            PdfObject wrapperObj = wrapper.getPdfObject();
            for (int i = 0; i < kArray.size(); i++) {
                PdfObject obj = kArray.get(i);
                if (obj == wrapperObj || obj.equals(wrapperObj.getIndirectReference())) {
                    wrapperIndex = i;
                    break;
                }
            }

            if (wrapperIndex < 0) {
                logger.debug("Could not find wrapper in root K array");
                return;
            }

            // Insert children at wrapper's position (before removing wrapper)
            for (int i = 0; i < childrenToMove.size(); i++) {
                PdfStructElem child = childrenToMove.get(i);
                wrapper.removeKid(child);
                child.getPdfObject().put(PdfName.P, root.getPdfObject());
                kArray.add(wrapperIndex + i, child.getPdfObject());
            }

            // Now remove the wrapper
            kArray.remove(wrapperIndex + childrenToMove.size());

            flattened++;
            logger.debug(
                    "Flattened {} with {} children into root at position {}",
                    wrapper.getRole().getValue(),
                    childrenToMove.size(),
                    wrapperIndex);
        } else {
            logger.debug(
                    "Cannot flatten {} - parent type {} not supported",
                    wrapper.getRole().getValue(),
                    parentNode.getClass().getSimpleName());
        }
    }

    // TODO: Move to a utility class
    private void removeFromParent(PdfStructElem elem, IStructureNode parentNode) {
        if (parentNode instanceof PdfStructElem parent) {
            parent.removeKid(elem);
        } else if (parentNode instanceof PdfStructTreeRoot root) {
            PdfObject kObj = root.getPdfObject().get(PdfName.K);
            if (kObj instanceof PdfArray kArray) {
                kArray.remove(elem.getPdfObject());
                if (elem.getPdfObject().getIndirectReference() != null) {
                    kArray.remove(elem.getPdfObject().getIndirectReference());
                }
            }
        }
    }

    @Override
    public String describe() {
        return "Flattened " + flattened + " unnecessary Part/Sect/Art wrapper(s)";
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
