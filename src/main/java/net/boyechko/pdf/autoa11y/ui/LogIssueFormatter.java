// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import java.util.StringJoiner;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/** Renders Issues with debugging-oriented detail for logs. */
public final class LogIssueFormatter implements IssueFormatter {

    @Override
    public String format(IssueLoc where) {
        return switch (where) {
            case null -> "";
            case IssueLoc.None n -> "";
            case IssueLoc.AtPage(var pageNum) -> " (" + Format.page(pageNum) + ")";
            case IssueLoc.AtObj(var objNum, var pageNum, var kind) ->
                    " (" + objLabel(kind, objNum) + withPage(pageNum) + ")";
            case IssueLoc.AtElem(var element, var pageNum, var role, var structPath) ->
                    " (" + joinElem(where.objNum(), pageNum, role, structPath) + ")";
            case IssueLoc.AtMcid(
                            var pageNum,
                            var mcid,
                            var ownerObjNum,
                            var role,
                            var structPath) ->
                    " (" + joinMcid(pageNum, mcid, ownerObjNum, role, structPath) + ")";
        };
    }

    private static String objLabel(IssueLoc.ObjKind kind, Integer objNum) {
        if (objNum == null) {
            return "obj. ?";
        }
        IssueLoc.ObjKind resolvedKind = kind != null ? kind : IssueLoc.ObjKind.GENERIC;
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

    private static String joinElem(
            Integer objNum, Integer pageNum, String role, String structPath) {
        StringJoiner joiner = new StringJoiner(", ");
        if (objNum != null) {
            joiner.add(Format.objNum(objNum));
        }
        if (pageNum != null) {
            joiner.add(Format.page(pageNum));
        }
        if (role != null && !role.isBlank()) {
            joiner.add("role=" + role);
        }
        return joiner.length() > 0 ? joiner.toString() : "elem";
    }

    private static String joinMcid(
            int pageNum, int mcid, Integer ownerObjNum, String role, String structPath) {
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
