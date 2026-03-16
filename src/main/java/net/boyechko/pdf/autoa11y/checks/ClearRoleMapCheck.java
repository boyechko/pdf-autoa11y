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
