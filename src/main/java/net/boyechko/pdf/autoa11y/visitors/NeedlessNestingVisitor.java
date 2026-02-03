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
package net.boyechko.pdf.autoa11y.visitors;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.fixes.FlattenNesting;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/** Visitor that detects Part/Sect/Art wrapper elements that add no semantic value. */
public class NeedlessNestingVisitor implements StructureTreeVisitor {

    private static final Set<String> WRAPPER_ROLES = Set.of("Part", "Sect", "Art");

    private final List<PdfStructElem> wrappersToFlatten = new ArrayList<>();
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Needless Nesting Check";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (WRAPPER_ROLES.contains(ctx.role()) && !isPageContainer(ctx.node())) {
            wrappersToFlatten.add(ctx.node());
        }
        return true;
    }

    @Override
    public void afterTraversal() {
        if (!wrappersToFlatten.isEmpty()) {
            IssueFix fix = new FlattenNesting(wrappersToFlatten);
            Issue issue =
                    new Issue(
                            IssueType.NEEDLESS_NESTING,
                            IssueSeverity.WARNING,
                            "Found " + wrappersToFlatten.size() + " Part/Sect/Art wrapper(s)",
                            fix);
            issues.add(issue);
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private boolean isPageContainer(PdfStructElem elem) {
        // Part with /Pg is a page-level container.
        if (!PdfName.Part.equals(elem.getRole())) {
            return false;
        }
        return elem.getPdfObject().containsKey(PdfName.Pg);
    }
}
