/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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

import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Removes /RoleMap from /StructTreeRoot. */
public class ClearRoleMapFix implements IssueFix {
    private static final int P_ROLEMAP = 5;

    private int clearedCount = 0;

    @Override
    public int priority() {
        return P_ROLEMAP;
    }

    @Override
    public void apply(DocContext ctx) {
        clearedCount = RoleMap.clear(ctx.doc());
    }

    @Override
    public String describe() {
        return "Cleared /RoleMap with " + clearedCount + " entries";
    }
}
