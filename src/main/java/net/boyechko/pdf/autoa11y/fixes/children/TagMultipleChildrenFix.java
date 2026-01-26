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
package net.boyechko.pdf.autoa11y.fixes.children;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Optional;
import net.boyechko.pdf.autoa11y.fixes.child.TagSingleChildFix;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TagMultipleChildrenFix implements IssueFix {

    protected static final Logger logger = LoggerFactory.getLogger(TagMultipleChildrenFix.class);

    protected final PdfStructElem parent;
    protected final List<PdfStructElem> kids;

    protected TagMultipleChildrenFix(PdfStructElem parent, List<PdfStructElem> kids) {
        this.parent = parent;
        this.kids = kids != null ? List.copyOf(kids) : List.of();
    }

    public static Optional<IssueFix> createIfApplicable(
            PdfStructElem parent, List<PdfStructElem> kids) {
        // Try each subclass factory method
        return WrapPairsOfLblPInLI.tryCreate(parent, kids)
                .or(() -> WrapPairsOfLblLBodyInLI.tryCreate(parent, kids))
                .or(() -> ChangePToLblInLI.tryCreate(parent, kids));
    }

    @Override
    public int priority() {
        return 20; // Structure fix - higher priority than single child fixes
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getParent() {
        return parent;
    }

    public List<PdfStructElem> getKids() {
        return kids;
    }

    public String getParentRole() {
        return parent.getRole().getValue();
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        if (otherFix instanceof TagSingleChildFix singleFix) {
            return getParentRole().equals(singleFix.getParentRole())
                    && singleFix.getParent().equals(parent)
                    && kids.contains(singleFix.getKid());
        }
        return false;
    }
}
