// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Locus;

/** Renders Issues for user-facing reports and terminal output. */
public final class UserIssueFormatter implements IssueFormatter {

    @Override
    public String format(Locus where) {
        return switch (where) {
            case null -> "";
            case Locus.None n -> "";
            case Locus.AtPage(var pageNum) -> " (" + Format.page(pageNum) + ")";
            case Locus.AtObj(var objNum, var pageNum, var kind) -> {
                if (pageNum != null) {
                    yield " (" + Format.page(pageNum) + ")";
                }
                yield objNum != null ? " (" + Format.objNum(objNum) + ")" : "";
            }
            case Locus.AtElem(var element, var pageNum, var role) -> {
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
            case Locus.AtMcid(var pageNum, var mcid, var ownerObjNum, var role) -> {
                if (role != null && !role.isBlank()) {
                    yield " (" + role + ", " + Format.page(pageNum) + ")";
                }
                yield " (" + Format.page(pageNum) + ")";
            }
        };
    }
}
