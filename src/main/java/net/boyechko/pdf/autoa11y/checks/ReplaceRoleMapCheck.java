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

import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.ReplaceRoleMapFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Replaces /RoleMap with the mappings supplied via sidecar config. */
public class ReplaceRoleMapCheck extends DocumentCheck {
    private final Map<String, String> mappings;

    public ReplaceRoleMapCheck(Map<String, String> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    @Override
    public String name() {
        return "Replace RoleMap";
    }

    @Override
    public String description() {
        return "Replace /RoleMap with supplied mappings";
    }

    @Override
    public String passedMessage() {
        return "RoleMap already matches desired mappings";
    }

    @Override
    public String failedMessage() {
        return "RoleMap needs replacement";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        Issue issue =
                new Issue(
                        IssueType.ROLEMAP_PRESENT,
                        IssueSev.INFO,
                        "Replacing RoleMap with " + mappings.size() + " mapping(s)",
                        new ReplaceRoleMapFix(mappings));
        return new IssueList(issue);
    }
}
