// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.fixes.ClearRoleMapFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Detects presence of /RoleMap and offers to remove it. */
public class ClearRoleMapCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Clear RoleMap";
    }

    @Override
    public String description() {
        return "Remove /RoleMap from the structure tree root";
    }

    @Override
    public String passedMessage() {
        return "No RoleMap present";
    }

    @Override
    public String failedMessage() {
        return "RoleMap found in structure tree root";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        if (!RoleMap.hasRoleMap(ctx.doc())) {
            return new IssueList();
        }
        Issue issue =
                new Issue(
                        IssueType.ROLEMAP_PRESENT,
                        IssueSev.INFO,
                        "RoleMap present in structure tree root",
                        new ClearRoleMapFix());
        return new IssueList(issue);
    }
}
