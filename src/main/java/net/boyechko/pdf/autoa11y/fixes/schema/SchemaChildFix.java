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
package net.boyechko.pdf.autoa11y.fixes.schema;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.fixes.SchemaValidationFix;

/** Schema fix that operates on a single child element. */
public abstract class SchemaChildFix extends SchemaValidationFix {

    protected final PdfStructElem kid;

    protected SchemaChildFix(PdfStructElem kid, PdfStructElem parent) {
        super(parent);
        this.kid = kid;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public String describe() {
        return "Fix a single child, " + getKidRole() + ", under its parent " + getParentRole();
    }

    public PdfStructElem getKid() {
        return kid;
    }

    public String getKidRole() {
        return kid.getRole().getValue();
    }
}
