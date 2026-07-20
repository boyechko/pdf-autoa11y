// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
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
