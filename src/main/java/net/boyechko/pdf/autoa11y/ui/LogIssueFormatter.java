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
        if (structPath != null && !structPath.isBlank()) {
            joiner.add("path=" + structPath);
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
        if (structPath != null && !structPath.isBlank()) {
            joiner.add("path=" + structPath);
        }
        return joiner.toString();
    }
}
