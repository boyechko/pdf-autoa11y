// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;

/** {@code !SET_ROLE <role>}: changes the element's role, noting the previous one in the receipt. */
record SetRoleInstruction(String roleName) implements ScribbledInstruction {

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        String prevRole = element.getRole().getValue();
        element.setRole(RoleMap.toPdfName(roleName));
        return receipt("SET_ROLE " + prevRole + " -> " + roleName);
    }
}
