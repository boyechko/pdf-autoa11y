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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.Geometry;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.MoveSiblingMcrIntoLink;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeChecker;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/** Detects Link tags that are missing link description. */
public class EmptyLinkTagCheck extends StructTreeChecker {

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Empty Link Tag Check";
    }

    @Override
    public String description() {
        return "Link elements should contain link description";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        // Get the raw IStructureNode children (includes MCRs, not just PdfStructElems)
        List<IStructureNode> kids = ctx.node().getKids();
        if (kids == null || kids.isEmpty()) {
            return true;
        }

        // Look for empty Link elements that follow MCRs
        for (int i = 1; i < kids.size(); i++) {
            IStructureNode prev = kids.get(i - 1);
            IStructureNode curr = kids.get(i);

            if (!(curr instanceof PdfStructElem linkElem)) {
                continue;
            }
            if (!linkElem.getRole().equals(PdfName.Link) || StructTree.hasMcr(linkElem)) {
                continue;
            }
            if (!(prev instanceof PdfMcr mcr) || mcr.getMcid() < 0) {
                continue;
            }

            PdfObjRef objRef = StructTree.findFirstObjRef(linkElem);
            if (objRef == null) {
                continue;
            }
            PdfDictionary annotDict = objRef.getReferencedObject();
            if (annotDict == null) {
                continue;
            }

            int pageNum = ctx.getPageNumber();
            if (pageNum <= 0) {
                continue;
            }

            Map<Integer, Rectangle> mcidBounds =
                    ctx.docCtx()
                            .getOrComputeMcidBounds(
                                    pageNum,
                                    () -> Content.extractBoundsForPage(ctx.doc().getPage(pageNum)));
            Rectangle mcrRect = mcidBounds.get(mcr.getMcid());
            Rectangle annotRect = Geometry.getAnnotationBounds(annotDict);
            if (!Geometry.boundsSimilar(mcrRect, annotRect)) {
                continue;
            }

            IssueFix fix = new MoveSiblingMcrIntoLink(linkElem, mcr.getMcid(), pageNum);
            Issue issue =
                    new Issue(
                            IssueType.EMPTY_LINK_TAG,
                            IssueSev.WARNING,
                            IssueLoc.atPageNum(pageNum),
                            "Link element (after/inside "
                                    + prev.getRole().getValue()
                                    + ") is missing link description",
                            fix);
            issues.add(issue);
        }

        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
