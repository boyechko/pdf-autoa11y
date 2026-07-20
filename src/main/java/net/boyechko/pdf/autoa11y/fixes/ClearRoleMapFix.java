// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Removes /RoleMap from /StructTreeRoot. */
public class ClearRoleMapFix implements IssueFix {

    private int clearedCount = 0;

    @Override
    public void apply(DocContext ctx) {
        clearedCount = RoleMap.clear(ctx.doc());
    }

    @Override
    public String describe() {
        return "Cleared /RoleMap with " + clearedCount + " entries";
    }
}
