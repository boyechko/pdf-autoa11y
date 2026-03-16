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
package net.boyechko.pdf.autoa11y.fixes;

import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Replaces /RoleMap with the supplied mappings. */
public class ReplaceRoleMapFix implements IssueFix {
    private static final int P_ROLEMAP = 5;

    private final Map<String, String> mappings;

    public ReplaceRoleMapFix(Map<String, String> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    @Override
    public int priority() {
        return P_ROLEMAP;
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
