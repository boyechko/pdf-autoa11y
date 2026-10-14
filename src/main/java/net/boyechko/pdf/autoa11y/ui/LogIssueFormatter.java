// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import java.util.StringJoiner;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Locus;

/** Renders Issues with debugging-oriented detail for logs. */
public final class LogIssueFormatter implements IssueFormatter {

    @Override
    public String format(Locus where) {
        return switch (where) {
            case null -> "";
            case Locus.None n -> "";
            case Locus.AtPage(var pageNum) -> " (" + Format.page(pageNum) + ")";
            case Locus.AtObj(var objNum, var pageNum, var kind) ->
                    " (" + objLabel(kind, objNum) + withPage(pageNum) + ")";
            case Locus.AtElem(var objNum, var pageNum, var role) ->
                    " (" + joinElem(objNum, pageNum, role) + ")";
            case Locus.AtMcid(var pageNum, var mcid, var ownerObjNum, var role) ->
                    " (" + joinMcid(pageNum, mcid, ownerObjNum, role) + ")";
        };
    }

    private static String objLabel(Locus.ObjKind kind, int objNum) {
        Locus.ObjKind resolvedKind = kind != null ? kind : Locus.ObjKind.GENERIC;
        return switch (resolvedKind) {
            case ANNOT -> "annot " + Format.objNum(objNum);
            case STRUCT_ELEM -> "struct " + Format.objNum(objNum);
            case FONT -> "font " + Format.objNum(objNum);
            case XOBJECT -> "xobject " + Format.objNum(objNum);
            case GENERIC -> Format.objNum(objNum);
        };
    }

    private static String withPage(Integer pageNum) {
        return pageNum != null ? ", " + Format.page(pageNum) : "";
    }

    private static String joinElem(int objNum, Integer pageNum, String role) {
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add(Format.objNum(objNum));
        if (pageNum != null) {
            joiner.add(Format.page(pageNum));
        }
        if (role != null && !role.isBlank()) {
            joiner.add("role=" + role);
        }
        return joiner.toString();
    }

    private static String joinMcid(int pageNum, int mcid, Integer ownerObjNum, String role) {
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add(Format.mcid(mcid));
        joiner.add(Format.page(pageNum));
        if (ownerObjNum != null) {
            joiner.add("owner " + Format.objNum(ownerObjNum));
        }
        if (role != null && !role.isBlank()) {
            joiner.add("role=" + role);
        }
        return joiner.toString();
    }
}
