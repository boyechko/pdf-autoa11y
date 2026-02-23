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
package net.boyechko.pdf.autoa11y.issues;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructureTree;

/** Represents the location of an accessibility issue found in a PDF document. */
public final class IssueLocation {
    private final Integer page; // null if document-level
    private final PdfStructElem element; // may be null if not applicable
    private final String path; // optional: struct tree path, object id, etc.

    public IssueLocation() {
        this(null, null, null);
    }

    public IssueLocation(String path) {
        this(null, path, null);
    }

    public IssueLocation(Integer page, String path) {
        this(page, path, null);
    }

    public IssueLocation(PdfStructElem element) {
        this(null, null, element);
    }

    public IssueLocation(PdfStructElem element, String path) {
        this(null, path, element);
    }

    public IssueLocation(Integer page, String path, PdfStructElem element) {
        this.page = page;
        this.path = path;
        this.element = element;
    }

    public Integer page() {
        return page;
    }

    public String path() {
        return path;
    }

    public PdfStructElem element() {
        return element;
    }

    public Integer objectId() {
        if (element == null || element.getPdfObject() == null) {
            return null;
        }
        int objNum = StructureTree.objNumber(element);
        return objNum >= 0 ? objNum : null;
    }

    public String toString() {
        Integer objId = objectId();
        String output = "";

        if (objId != null) {
            output += Format.obj(objId);
        }
        if (page != null) {
            output += " (" + Format.page(page) + ")";
        }
        if (path != null) {
            output += " (" + path + ")";
        }
        return output.trim();
    }
}
