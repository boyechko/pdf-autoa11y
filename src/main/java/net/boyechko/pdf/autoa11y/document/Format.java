// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/**
 * Formatting utilities for PDF entities in log messages and reports.
 *
 * <p>The {@link #elem} overloads produce a compact label that combines role, object number, and
 * optionally page number, e.g. {@code "P (obj. #23, p. 5)"}. Use {@link #obj} and {@link #page} for
 * primitive tokens when only those components are needed.
 */
public final class Format {
    /** Default width for text excerpts shown in messages, reports, and diagrams. */
    public static final int TRUNCATE_MAX_WIDTH = 30;

    private Format() {}

    /** Returns a short label for a structure element: role and object number. */
    public static String elem(PdfStructElem elem) {
        return elem(elem, 0);
    }

    /**
     * Returns a short label for a structure element with its page number.
     *
     * <p>The page is omitted when {@code pageNum <= 0}.
     */
    public static String elem(PdfStructElem elem, int pageNum) {
        return Label.of(DocValue.Role.of(elem))
                .add(DocValue.ObjNum.of(elem))
                .add(pageNum > 0 ? page(pageNum) : null)
                .separator(", ")
                .wrap("(", ")")
                .toString();
    }

    /** Returns a short label for a structure element, resolving the page via {@code ctx}. */
    public static String elem(PdfStructElem elem, DocContext ctx) {
        DocValue.ObjNum obj = DocValue.ObjNum.of(elem);
        int pageNum = obj != null ? ctx.getPageNumber(obj.value()) : 0;
        return elem(elem, pageNum);
    }

    /** Returns a short label for a PDF object number. */
    public static String objNum(int objNum) {
        return new DocValue.ObjNum(objNum).toString();
    }

    /** Returns a short label for a page number. */
    public static String page(int pageNum) {
        return new DocValue.PageNum(pageNum).toString();
    }

    /** Returns a short label for a marked content identifier. */
    public static String mcid(int mcid) {
        return new DocValue.Mcid(mcid).toString();
    }

    /** Returns {@code text} clipped to {@link #TRUNCATE_MAX_WIDTH} characters. */
    public static String truncate(String text) {
        return truncate(text, TRUNCATE_MAX_WIDTH);
    }

    /**
     * Returns {@code text} clipped to at most {@code maxWidth} characters, with the last character
     * replaced by an ellipsis when clipping occurs.
     */
    public static String truncate(String text, int maxWidth) {
        if (text == null || text.length() <= maxWidth) return text;
        return text.substring(0, maxWidth - 1) + "…";
    }

    /**
     * Returns a parenthesized location string for an issue, e.g. {@code " (obj. #42, p. 3)"}.
     * Returns empty string when no location info is available.
     */
    public static String loc(IssueLoc where) {
        return switch (where) {
            case null -> "";
            case IssueLoc.None n -> "";
            case IssueLoc.AtPage(var pageNum) -> " (" + page(pageNum) + ")";
            case IssueLoc.AtObj(var objNum, var pageNum, var kind) -> {
                String s = " (" + typedObj(kind, objNum);
                if (pageNum != null) s += ", " + page(pageNum);
                yield s + ")";
            }
            case IssueLoc.AtElem(var element, var pageNum, var role, var structPath) -> {
                DocValue.ObjNum obj = DocValue.ObjNum.of(element);
                if (obj == null && pageNum == null) {
                    yield (role != null && !role.isBlank()) ? " (" + role + ")" : "";
                }
                String s = " (";
                if (obj != null) {
                    s += objNum(obj.value());
                    if (pageNum != null) s += ", " + page(pageNum);
                } else {
                    s += page(pageNum);
                }
                yield s + ")";
            }
            case IssueLoc.AtMcid(
                    var pageNum,
                    var mcid,
                    var ownerObjNum,
                    var role,
                    var structPath) -> {
                String s = " (" + mcid(mcid);
                s += ", " + page(pageNum);
                if (ownerObjNum != null) s += ", " + objNum(ownerObjNum);
                yield s + ")";
            }
        };
    }

    private static String typedObj(IssueLoc.ObjKind kind, int objNum) {
        IssueLoc.ObjKind resolvedKind = kind != null ? kind : IssueLoc.ObjKind.GENERIC;
        return switch (resolvedKind) {
            case ANNOT -> "annot " + objNum(objNum);
            case STRUCT_ELEM -> "struct " + objNum(objNum);
            case FONT -> "font " + objNum(objNum);
            case XOBJECT -> "xobject " + objNum(objNum);
            case GENERIC -> objNum(objNum);
        };
    }
}
