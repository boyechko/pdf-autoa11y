// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Replaces /RoleMap with the supplied mappings. */
public class ReplaceRoleMapFix implements IssueFix {

    private final Map<String, String> mappings;

    public ReplaceRoleMapFix(Map<String, String> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    @Override
    public void apply(DocContext ctx) {
        RoleMap.replace(ctx.doc(), mappings);
    }

    @Override
    public String describe() {
        return "Replaced /RoleMap with " + mappings.size() + " mapping(s)";
    }
}
