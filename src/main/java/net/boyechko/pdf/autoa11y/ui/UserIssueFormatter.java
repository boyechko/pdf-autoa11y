// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/** Renders Issues for user-facing reports and terminal output. */
public final class UserIssueFormatter implements IssueFormatter {

    @Override
    public String format(IssueLoc where) {
        return switch (where) {
            case null -> "";
            case IssueLoc.None n -> "";
            case IssueLoc.AtPage(var pageNum) -> " (" + Format.page(pageNum) + ")";
            case IssueLoc.AtObj(var objNum, var pageNum, var kind) -> {
                if (pageNum != null) {
                    yield " (" + Format.page(pageNum) + ")";
                }
                yield objNum != null ? " (" + Format.objNum(objNum) + ")" : "";
            }
            case IssueLoc.AtElem(var element, var pageNum, var role, var structPath) -> {
                if (pageNum != null && role != null && !role.isBlank()) {
                    yield " (" + role + ", " + Format.page(pageNum) + ")";
                }
                if (pageNum != null) {
                    yield " (" + Format.page(pageNum) + ")";
                }
                if (role != null && !role.isBlank()) {
                    yield " (" + role + ")";
                }
                Integer objNum = where.objNum();
                yield objNum != null ? " (" + Format.objNum(objNum) + ")" : "";
            }
            case IssueLoc.AtMcid(
                    var pageNum,
                    var mcid,
                    var ownerObjNum,
                    var role,
                    var structPath) -> {
                if (role != null && !role.isBlank()) {
                    yield " (" + role + ", " + Format.page(pageNum) + ")";
                }
                yield " (" + Format.page(pageNum) + ")";
            }
        };
    }
}
