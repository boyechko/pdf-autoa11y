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

/** Detects grouping elements that add no semantic value. */
public class NeedlessNestingRule implements Rule {

    private static final Set<PdfName> GROUPING_ROLES =
            Set.of(PdfName.Part, PdfName.Sect, PdfName.Art);

    @Override
    public String name() {
        return "Grouping elements should not be overused";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return new IssueList();
        }

        IssueList issues = new IssueList();
        List<PdfStructElem> elementsToFlatten = new ArrayList<>();

        findGroupingElements(root, elementsToFlatten);

        if (!elementsToFlatten.isEmpty()) {
            IssueFix fix = new FlattenNesting(elementsToFlatten);
            Issue issue =
                    new Issue(
                            IssueType.NEEDLESS_NESTING,
                            IssueSeverity.WARNING,
                            "Found " + elementsToFlatten.size() + " grouping elements",
                            fix);
            issues.add(issue);
        }

        return issues;
    }

    private void findGroupingElements(PdfStructTreeRoot root, List<PdfStructElem> toFlatten) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                collectGroupingElementsRecursively(elem, toFlatten);
            }
        }
    }

    private void collectGroupingElementsRecursively(
            PdfStructElem elem, List<PdfStructElem> toFlatten) {
        if (GROUPING_ROLES.contains(elem.getRole()) && !isPageContainer(elem)) {
            toFlatten.add(elem);
        }

        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    collectGroupingElementsRecursively(childElem, toFlatten);
                }
            }
        }
    }

    /** A Part with /Pg is a page container — it groups content for a specific page. */
    private boolean isPageContainer(PdfStructElem elem) {
        if (!PdfName.Part.equals(elem.getRole())) {
            return false;
        }
        return elem.getPdfObject().containsKey(PdfName.Pg);
    }
}
