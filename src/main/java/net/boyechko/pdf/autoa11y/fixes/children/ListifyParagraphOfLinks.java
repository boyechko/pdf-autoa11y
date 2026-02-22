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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts a P element containing only links to an L element. */
public final class ListifyParagraphOfLinks extends TagMultipleChildrenFix {
    private static final Logger logger = LoggerFactory.getLogger(ListifyParagraphOfLinks.class);
    private static int MINIMUM_KIDS_COUNT = 2;

    public ListifyParagraphOfLinks(PdfStructElem parent, List<PdfStructElem> kids) {
        super(parent, kids);
    }

    public static IssueFix tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
        logger.debug(
                "Trying to create ListifyParagraphOfLinks for P element with {} kids", kids.size());
        PdfName parentRole = parent.getRole();
        if (!PdfName.P.equals(parentRole) || kids.size() < MINIMUM_KIDS_COUNT) {
            logger.debug(
                    "ListifyParagraphOfLinks not applicable: parent role is {} and kids count is {}",
                    parentRole,
                    kids.size());
            return null;
        }

        if (!kids.stream().allMatch(kid -> PdfName.Link.equals(kid.getRole()))) {
            logger.debug("ListifyParagraphOfLinks not applicable: kids are not all links");
            return null;
        }

        // Reject if parent has non-struct-elem kids (MCRs/OBJRs) that would be
        // orphaned under L — only convert when ALL kids are struct elements.
        var allKids = parent.getKids();
        if (allKids != null && allKids.size() != kids.size()) {
            logger.debug(
                    "ListifyParagraphOfLinks not applicable: parent has {} total kids but {} struct kids",
                    allKids.size(),
                    kids.size());
            return null;
        }

        return new ListifyParagraphOfLinks(parent, kids);
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        parent.setRole(PdfName.L);
        for (PdfStructElem kid : kids) {
            PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
            PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
            parent.addKid(li);
            li.addKid(lBody);
            lBody.addKid(kid);
            parent.removeKid(kid);
        }
    }

    @Override
    public String describe() {
        int objNum = StructureTree.objNumber(parent);
        return "Listified P element with links to L element " + Format.obj(objNum);
    }

    @Override
    public String describe(DocumentContext ctx) {
        return describe();
    }
}
