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
