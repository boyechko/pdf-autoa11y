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
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a tagged element to an artifact by removing it from the structure tree. The content
 * remains visible but is no longer part of the accessibility tree.
 */
public class ConvertToArtifact implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ConvertToArtifact.class);
    private static final int P_ARTIFACT = 12; // After doc setup (10), before flatten (15)

    private final PdfStructElem element;

    public ConvertToArtifact(PdfStructElem element) {
        this.element = element;
    }

    @Override
    public int priority() {
        return P_ARTIFACT;
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        IStructureNode parent = element.getParent();
        if (parent == null) {
            logger.debug("Element already has no parent, skipping");
            return;
        }

        if (parent instanceof PdfStructElem parentElem) {
            parentElem.removeKid(element);
            logger.debug(
                    "Removed {} from parent {}",
                    element.getRole().getValue(),
                    parentElem.getRole().getValue());
        } else if (parent instanceof PdfStructTreeRoot root) {
            PdfObject kObj = root.getPdfObject().get(PdfName.K);
            if (kObj instanceof PdfArray kArray) {
                kArray.remove(element.getPdfObject());
                if (element.getPdfObject().getIndirectReference() != null) {
                    kArray.remove(element.getPdfObject().getIndirectReference());
                }
                logger.debug("Removed {} from structure tree root", element.getRole().getValue());
            }
        }
    }

    @Override
    public String describe() {
        String role = element.getRole() != null ? element.getRole().getValue() : "unknown";
        int objNum =
                element.getPdfObject().getIndirectReference() != null
                        ? element.getPdfObject().getIndirectReference().getObjNumber()
                        : 0;
        return "Converted " + role + " (object #" + objNum + ") to artifact";
    }

    @Override
    public String describe(DocumentContext ctx) {
        String role = element.getRole() != null ? element.getRole().getValue() : "unknown";
        int objNum =
                element.getPdfObject().getIndirectReference() != null
                        ? element.getPdfObject().getIndirectReference().getObjNumber()
                        : 0;
        int pageNum = ctx.getPageNumber(objNum);
        String pageInfo = (pageNum > 0) ? " (p. " + pageNum + ")" : "";
        return "Converted " + role + " (object #" + objNum + ")" + pageInfo + " to artifact";
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        if (otherFix instanceof ConvertToArtifact other) {
            return isDescendantOf(other.element, this.element);
        }
        return false;
    }

    private boolean isDescendantOf(PdfStructElem candidate, PdfStructElem ancestor) {
        IStructureNode parent = candidate.getParent();
        while (parent != null) {
            if (parent == ancestor) {
                return true;
            }
            if (parent instanceof PdfStructElem parentElem) {
                parent = parentElem.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    public PdfStructElem getElement() {
        return element;
    }
}
