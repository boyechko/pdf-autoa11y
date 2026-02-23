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
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;

/**
 * Formatting utilities for PDF entities in log messages and reports.
 *
 * <p>The {@link #elem} overloads produce a compact label that combines role, object number, and
 * optionally page number, e.g. {@code "P (obj. #23, p. 5)"}. Use {@link #obj} and {@link #page} for
 * primitive tokens when only those components are needed.
 */
public final class Format {
    private Format() {}

    /** Returns a short label for a structure element: role and object number. */
    public static String elem(PdfStructElem elem) {
        String role = elem.getRole() != null ? elem.getRole().getValue() : "?";
        int objNum = StructureTree.objNumber(elem);
        return objNum >= 0 ? role + " (obj. #" + objNum + ")" : role;
    }

    /**
     * Returns a short label for a structure element with its page number.
     *
     * <p>The page is omitted when {@code pageNum <= 0}.
     */
    public static String elem(PdfStructElem elem, int pageNum) {
        String role = elem.getRole() != null ? elem.getRole().getValue() : "?";
        int objNum = StructureTree.objNumber(elem);
        if (objNum < 0) return role;
        if (pageNum > 0) return role + " (obj. #" + objNum + ", p. " + pageNum + ")";
        return role + " (obj. #" + objNum + ")";
    }

    /** Returns a short label for a structure element, resolving the page via {@code ctx}. */
    public static String elem(PdfStructElem elem, DocumentContext ctx) {
        int objNum = StructureTree.objNumber(elem);
        int pageNum = ctx.getPageNumber(objNum);
        return elem(elem, pageNum);
    }

    /** Returns a short label for a PDF object number. */
    public static String obj(int objNum) {
        return "obj. #" + objNum;
    }

    /** Returns a short label for a PDF object number and page number. */
    public static String obj(int objNum, int pageNum) {
        return obj(objNum) + " (" + page(pageNum) + ")";
    }

    /** Returns a short label for a page number. */
    public static String page(int pageNum) {
        return "p. " + pageNum;
    }

    /** Returns a short label for a marked content identifier. */
    public static String mcid(int mcid) {
        return "MCID #" + mcid;
    }

    /** Returns a short label for a marked content identifier on a page. */
    public static String mcid(int mcid, int pageNum) {
        return mcid(mcid) + " (" + page(pageNum) + ")";
    }

    /**
     * Returns a parenthesized location string for an issue, e.g. {@code " (obj. #42, p. 3)"}.
     * Returns empty string when no location info is available.
     */
    public static String loc(IssueLocation where) {
        if (where == null) return "";
        Integer objId = where.objectId();
        Integer page = where.page();
        if (objId == null && page == null) return "";
        StringBuilder sb = new StringBuilder(" (");
        if (objId != null) sb.append(obj(objId));
        if (objId != null && page != null) sb.append(", ");
        if (page != null) sb.append(page(page));
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns a parenthesized location string for an object number and page number. e.g. {@code "
     * (obj. #42, p. 3)"}.
     */
    public static String loc(int objNum, int pageNum) {
        return " (" + obj(objNum) + ", " + page(pageNum) + ")";
    }
}
