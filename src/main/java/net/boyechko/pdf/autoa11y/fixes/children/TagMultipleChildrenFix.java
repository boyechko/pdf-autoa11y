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

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.fixes.child.TagSingleChildFix;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for fixes that involve multiple child elements. */
public abstract class TagMultipleChildrenFix implements IssueFix {

    protected static final Logger logger = LoggerFactory.getLogger(TagMultipleChildrenFix.class);

    protected final PdfStructElem parent;
    protected final List<PdfStructElem> kids;

    protected TagMultipleChildrenFix(PdfStructElem parent, List<? extends IStructureNode> kids) {
        this.parent = parent;
        this.kids =
                kids != null
                        ? kids.stream().map(kid -> (PdfStructElem) kid).collect(Collectors.toList())
                        : List.of();
    }

    public static IssueFix createIfApplicable(PdfStructElem parent, List<PdfStructElem> kids) {
        IssueFix fix = WrapPairsOfLblPInLI.tryCreate(parent, kids);
        if (fix != null) return fix;

        fix = WrapPairsOfLblLBodyInLI.tryCreate(parent, kids);
        if (fix != null) return fix;

        fix = ChangePToLblInLI.tryCreate(parent, kids);
        if (fix != null) return fix;

        return null;
    }

    @Override
    public int priority() {
        return 20; // Structure fix - higher priority than single child fixes
    }

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
