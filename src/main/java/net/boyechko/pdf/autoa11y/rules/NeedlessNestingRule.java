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
package net.boyechko.pdf.autoa11y.rules;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.FlattenNesting;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects and removes all Part/Sect/Art elements from the structure tree. */
public class NeedlessNestingRule implements Rule {

    private static final Set<PdfName> WRAPPER_ROLES =
            Set.of(PdfName.Part, PdfName.Sect, PdfName.Art);

    @Override
    public String name() {
        return "Needless Nesting Check";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return new IssueList();
        }

        IssueList issues = new IssueList();
        List<PdfStructElem> elementsToFlatten = new ArrayList<>();

        findWrapperElements(root, elementsToFlatten);

        if (!elementsToFlatten.isEmpty()) {
            IssueFix fix = new FlattenNesting(elementsToFlatten);
            Issue issue =
                    new Issue(
                            IssueType.NEEDLESS_NESTING,
                            IssueSeverity.WARNING,
                            "Found " + elementsToFlatten.size() + " Part/Sect/Art wrapper(s)",
                            fix);
            issues.add(issue);
        }

        return issues;
    }

    private void findWrapperElements(PdfStructTreeRoot root, List<PdfStructElem> toFlatten) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                collectWrappersRecursively(elem, toFlatten);
            }
        }
    }

    private void collectWrappersRecursively(PdfStructElem elem, List<PdfStructElem> toFlatten) {
        if (WRAPPER_ROLES.contains(elem.getRole())) {
            toFlatten.add(elem);
        }

        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    collectWrappersRecursively(childElem, toFlatten);
                }
            }
        }
    }
}
