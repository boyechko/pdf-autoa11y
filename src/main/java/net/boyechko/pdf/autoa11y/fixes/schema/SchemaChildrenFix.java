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

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.fixes.SchemaValidationFix;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Schema fix that operates on multiple child elements. */
public abstract class SchemaChildrenFix extends SchemaValidationFix {

    protected static final Logger logger = LoggerFactory.getLogger(SchemaChildrenFix.class);

    protected final List<PdfStructElem> kids;

    protected SchemaChildrenFix(PdfStructElem parent, List<? extends IStructureNode> kids) {
        super(parent);
        this.kids =
                kids != null
                        ? kids.stream().map(kid -> (PdfStructElem) kid).collect(Collectors.toList())
                        : List.of();
    }

    @Override
    public int priority() {
        return 20; // Structure fix - higher priority than single child fixes
    }

    public List<PdfStructElem> getKids() {
        return kids;
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        if (this == otherFix) return true;
        if (otherFix instanceof SchemaChildFix singleFix) {
            return getParentRole().equals(singleFix.getParentRole())
                    && singleFix.getParent().equals(parent)
                    && kids.contains(singleFix.getKid());
        }
        return false;
    }
}
