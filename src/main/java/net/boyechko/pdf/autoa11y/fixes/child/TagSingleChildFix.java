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
package net.boyechko.pdf.autoa11y.fixes.child;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Optional;
import net.boyechko.pdf.autoa11y.issues.IssueFix;

public abstract class TagSingleChildFix implements IssueFix {

    protected final PdfStructElem kid;
    protected final PdfStructElem parent;

    protected TagSingleChildFix(PdfStructElem kid, PdfStructElem parent) {
        this.kid = kid;
        this.parent = parent;
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem kid, PdfStructElem parent) {
        // Try each subclass factory method
        return WrapInLI.tryCreate(kid, parent)
                .or(() -> TreatLblFigureAsBullet.tryCreate(kid, parent));
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public String describe() {
        return "Fix a single child, " + getKidRole() + ", under its parent " + getParentRole();
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getKid() {
        return kid;
    }

    public String getKidRole() {
        return kid.getRole().getValue();
    }

    public PdfStructElem getParent() {
        return parent;
    }

    public String getParentRole() {
        return parent.getRole().getValue();
    }
}
