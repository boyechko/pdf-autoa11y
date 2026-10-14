// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Objects;

/** Represents the location of an accessibility issue found in a PDF document. */
public sealed interface Locus {
    /** No location — a document-wide issue (e.g. missing metadata). */
    record None() implements Locus {}

    /** A whole page, 1-based. */
    record AtPage(int pageNum) implements Locus {}

    /** An indirect object such as an annotation or font; pageNum is an optional context. */
    record AtObj(Integer objNum, Integer pageNum, ObjKind kind) implements Locus {
        public AtObj {
            Objects.requireNonNull(objNum, "objNum");
        }
    }

    /** A structure element; pageNum and role are optional reporting context. */
    record AtElem(Integer objNum, Integer pageNum, String roleName) implements Locus {
        public AtElem {
            Objects.requireNonNull(objNum, "objNum");
        }
    }

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

    /**
     * Element location with page and role resolved from the document; none() on null input. An
     * element stored as a direct object has no object number, so it falls back to its page, if any.
     */
    static Locus atElem(DocContext ctx, PdfStructElem element) {
        if (ctx == null || element == null) {
            return none();
        }
        int objNum = StructTree.objNum(element);
        int pageNum = StructTree.pageOf(element, ctx);
        if (objNum < 0) {
            return pageNum > 0 ? atPage(pageNum) : none();
        }
        Integer maybePage = pageNum > 0 ? pageNum : null;
        String role = element.getRole() != null ? StructTree.mappedRole(element) : null;
        return new AtElem(objNum, maybePage, role);
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
            case AtElem(var objNum, var pageNum, var role) -> pageNum;
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var role) -> pageNum;
            default -> null;
        };
    }

    /** Returns the structure role if available, null otherwise. */
    default String role() {
        return switch (this) {
            case AtElem(var objNum, var pageNum, var roleName) -> roleName;
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var roleName) -> roleName;
            default -> null;
        };
    }

    /** Returns object number if available, null otherwise. */
    default Integer objNum() {
        return switch (this) {
            case AtObj(var objNum, var pageNum, var kind) -> objNum;
            case AtElem(var objNum, var pageNum, var role) -> objNum;
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var role) -> ownerObjNum;
            default -> null;
        };
    }
}
