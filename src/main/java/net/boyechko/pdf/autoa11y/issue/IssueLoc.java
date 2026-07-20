// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;

/** Represents the location of an accessibility issue found in a PDF document. */
public sealed interface IssueLoc {
    /** No location — a document-wide issue (e.g. missing metadata). */
    record None() implements IssueLoc {}

    /** A whole page, 1-based. */
    record AtPage(int pageNum) implements IssueLoc {}

    /** An indirect object such as an annotation or font; pageNum is optional context. */
    record AtObj(Integer objNum, Integer pageNum, ObjKind kind) implements IssueLoc {}

    /** A structure element; pageNum, role, and structPath are optional reporting context. */
    record AtElem(PdfStructElem element, Integer pageNum, String role, String structPath)
            implements IssueLoc {}

    /** A marked-content sequence on a page; ownerObjNum is the owning structure element. */
    record AtMcid(int pageNum, int mcid, Integer ownerObjNum, String role, String structPath)
            implements IssueLoc {}

    /** What kind of indirect object an {@link AtObj} points at. */
    enum ObjKind {
        ANNOT,
        STRUCT_ELEM,
        FONT,
        XOBJECT,
        GENERIC
    }

    static IssueLoc none() {
        return new None();
    }

    static IssueLoc atPage(int pageNum) {
        return new AtPage(pageNum);
    }

    static IssueLoc atObj(int objNum, Integer pageNum, ObjKind kind) {
        return new AtObj(objNum, pageNum, kind != null ? kind : ObjKind.GENERIC);
    }

    static IssueLoc atElem(PdfStructElem element) {
        return new AtElem(element, null, null, null);
    }

    static IssueLoc atElem(PdfStructElem element, Integer pageNum, String role, String structPath) {
        return new AtElem(element, pageNum, role, structPath);
    }

    /** Element location with page and role resolved from the document; none() on null input. */
    static IssueLoc atElem(DocContext ctx, PdfStructElem element) {
        if (ctx == null || element == null) {
            return none();
        }
        int pageNum = StructTree.pageOf(element, ctx);
        Integer maybePage = pageNum > 0 ? pageNum : null;
        String role = element.getRole() != null ? element.getRole().getValue() : null;
        return new AtElem(element, maybePage, role, null);
    }

    static IssueLoc atMcid(
            int pageNum, int mcid, Integer ownerObjNum, String role, String structPath) {
        return new AtMcid(pageNum, mcid, ownerObjNum, role, structPath);
    }

    /** Returns page number if available, null otherwise. */
    default Integer page() {
        return switch (this) {
            case AtPage(var pageNum) -> pageNum;
            case AtObj(var objNum, var pageNum, var kind) -> pageNum;
            case AtElem(var element, var pageNum, var role, var structPath) ->
                    pageNum != null ? pageNum : null;
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var role, var structPath) ->
                    pageNum;
            default -> null;
        };
    }

    /** Returns object number if available, null otherwise. */
    default Integer objNum() {
        return switch (this) {
            case AtObj(var objNum, var pageNum, var kind) -> objNum;
            case AtElem(var element, var pageNum, var role, var structPath) -> {
                int resolvedObjNum = StructTree.objNum(element);
                yield resolvedObjNum >= 0 ? resolvedObjNum : null;
            }
            case AtMcid(var pageNum, var mcid, var ownerObjNum, var role, var structPath) ->
                    ownerObjNum;
            default -> null;
        };
    }
}
