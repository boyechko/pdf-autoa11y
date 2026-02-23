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

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLoc;
import net.boyechko.pdf.autoa11y.issues.IssueSev;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/**
 * Reports meaningful Figure elements that lack alt text. These are content images large enough to
 * convey information but without descriptions for screen readers. No automatic fix — these need
 * human-written alt text.
 */
public class MissingAltTextVisitor implements StructureTreeVisitor {
    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Missing Alt Text Visitor";
    }

    @Override
    public String description() {
        return "Content images should have alt text";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!PdfName.Figure.equals(ctx.node().getRole())) {
            return true;
        }
        if (ctx.node().getAlt() != null) {
            return true;
        }

        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return true;
        }

        if (!hasImageMcr(ctx, pageNumber)) {
            return true;
        }

        Rectangle bounds = Content.getBoundsForElement(ctx.node(), ctx.docCtx(), pageNumber);
        if (bounds == null || !MistaggedArtifactVisitor.isMeaningfulSize(bounds)) {
            return true;
        }

        Issue issue =
                new Issue(
                        IssueType.FIGURE_MISSING_ALT,
                        IssueSev.WARNING,
                        new IssueLoc(ctx.node()),
                        "Figure without alt text");
        issues.add(issue);
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    // TODO: This is a duplicate of the method in MistaggedArtifactVisitor
    private boolean hasImageMcr(VisitorContext ctx, int pageNumber) {
        for (PdfMcr mcr : StructureTree.collectMcrs(ctx.node())) {
            if (mcr instanceof PdfObjRef) {
                continue;
            }
            int mcid = mcr.getMcid();
            if (mcid < 0) {
                continue;
            }

            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) {
                continue;
            }
            int pageNum = ctx.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                continue;
            }

            Map<Integer, Set<Content.ContentKind>> contentKinds =
                    ctx.docCtx()
                            .getOrComputeContentKinds(
                                    pageNum,
                                    () ->
                                            Content.extractContentKindsForPage(
                                                    ctx.doc().getPage(pageNum)));
            Set<Content.ContentKind> kinds = contentKinds.get(mcid);
            if (kinds != null && kinds.contains(Content.ContentKind.IMAGE)) {
                return true;
            }
        }
        return false;
    }
}
