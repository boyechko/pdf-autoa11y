// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/** Represents the location of an accessibility issue found in a PDF document. */
public sealed interface Locus {
    /** No location — a document-wide issue (e.g. missing metadata). */
    record None() implements Locus {}

    /** A whole page, 1-based. */
    record AtPage(int pageNum) implements Locus {}

    /** An indirect object such as an annotation or font; pageNum is an optional context. */
    record AtObj(Integer objNum, Integer pageNum, ObjKind kind) implements Locus {}

    /** A structure element; pageNum and role are optional reporting context. */
    record AtElem(PdfStructElem element, Integer pageNum, String roleName) implements Locus {}

    /** A marked-content sequence on a page; ownerObjNum is the owning structure element. */
    record AtMcid(int pageNum, int mcid, Integer ownerObjNum, String roleName) implements Locus {}

    /** What kind of indirect object an {@link AtObj} points at. */
    enum ObjKind {
        ANNOT,
        STRUCT_ELEM,
        FONT,
        XOBJECT,
        GENERIC
    }

    // --- Factory Methods ---

    static Locus none() {
        return new None();
    }

    static Locus atPage(int pageNum) {
        return new AtPage(pageNum);
    }

    static Locus atObj(int objNum, Integer pageNum, ObjKind kind) {
        return new AtObj(objNum, pageNum, kind != null ? kind : ObjKind.GENERIC);
    }

    /** Element location with page and role resolved from the document; none() on null input. */
    static Locus atElem(DocContext ctx, PdfStructElem element) {
        if (ctx == null || element == null) {
            return none();
        }
        int pageNum = StructTree.pageOf(element, ctx);
        Integer maybePage = pageNum > 0 ? pageNum : null;
        String role = element.getRole() != null ? StructTree.mappedRole(element) : null;
        return new AtElem(element, maybePage, role);
    }

    static Locus atMcid(int pageNum, int mcid, Integer ownerObjNum, String role) {
        return new AtMcid(pageNum, mcid, ownerObjNum, role);
    }

    // --- Default Methods ---

    /** Returns page number if available, null otherwise. */
    default Integer page() {
        return switch (this) {
            case AtPage(var pageNum) -> pageNum;
            case AtObj(var objNum, var pageNum, var kind) -> pageNum;
            case AtElem(var element, var pageNum, var role) -> pageNum;
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var role) -> pageNum;
            default -> null;
        };
    }

    /** Returns the structure role if available, null otherwise. */
    default String role() {
        return switch (this) {
            case AtElem(var element, var pageNum, var roleName) -> {
                if (roleName != null) {
                    yield roleName;
                }
                yield element != null && element.getRole() != null
                        ? element.getRole().getValue()
                        : null;
            }
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var roleName) -> roleName;
            default -> null;
        };
    }

    /** Returns object number if available, null otherwise. */
    default Integer objNum() {
        return switch (this) {
            case AtObj(var objNum, var pageNum, var kind) -> objNum;
            case AtElem(var element, var pageNum, var role) -> {
                int resolvedObjNum = StructTree.objNum(element);
                yield resolvedObjNum >= 0 ? resolvedObjNum : null;
            }
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var role) -> ownerObjNum;
            default -> null;
        };
    }
}
