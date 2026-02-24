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
package net.boyechko.pdf.autoa11y.issue;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;

/** Represents the location of an accessibility issue found in a PDF document. */
public sealed interface IssueLoc {
    record None() implements IssueLoc {}

    record AtPage(int pageNum) implements IssueLoc {}

    record AtObj(Integer objNum, Integer pageNum, ObjKind kind) implements IssueLoc {}

    record AtElem(PdfStructElem element, Integer pageNum, String role, String structPath)
            implements IssueLoc {}

    record AtMcid(int pageNum, int mcid, Integer ownerObjNum, String role, String structPath)
            implements IssueLoc {}

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

    static IssueLoc atElem(DocContext ctx, PdfStructElem element) {
        if (ctx == null || element == null) {
            return none();
        }
        int pageNum = StructTree.determinePageNumber(ctx, element);
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
