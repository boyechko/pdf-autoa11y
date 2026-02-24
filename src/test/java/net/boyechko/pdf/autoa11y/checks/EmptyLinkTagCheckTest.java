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

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Map;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

class EmptyLinkTagCheckTest extends PdfTestBase {

    @Test
    void detectsAndMovesSiblingMcrIntoLink() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            PdfPage page = pdfDoc.addNewPage();

            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"), page);
            document.addKid(p);

            PdfMcr mcr = new PdfMcrDictionary(page, p);
            p.addKid(mcr);

            Rectangle mcrRect = new Rectangle(100, 700, 60, 14);

            PdfLinkAnnotation linkAnnot =
                    new PdfLinkAnnotation(new Rectangle(mcrRect))
                            .setAction(PdfAction.createURI("https://example.com"));
            page.addAnnotation(-1, linkAnnot, false);

            PdfStructElem linkElem = new PdfStructElem(pdfDoc, PdfName.Link, page);
            p.addKid(linkElem);
            int structParentIndex = pdfDoc.getNextStructParentIndex();
            PdfObjRef objRef = new PdfObjRef(linkAnnot, linkElem, structParentIndex);
            linkElem.addKid(objRef);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            ctx.getOrComputeMcidBounds(1, () -> Map.of(mcr.getMcid(), mcrRect));
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(new EmptyLinkTagCheck());
            IssueList issues = walker.walk(root, ctx);

            assertEquals(1, issues.size(), "Expected one missing-content Link issue");
            Issue issue = issues.get(0);
            assertEquals(IssueType.EMPTY_LINK_TAG, issue.type());

            issue.fix().apply(ctx);

            assertEquals(1, p.getKids().size(), "P should now have only Link child");
            PdfStructElem updatedLink = (PdfStructElem) p.getKids().get(0);
            assertEquals(2, updatedLink.getKids().size(), "Link should contain MCR and OBJR");
            assertTrue(
                    updatedLink.getKids().get(0) instanceof PdfMcr,
                    "First Link child should be MCR");
            assertTrue(
                    updatedLink.getKids().get(1) instanceof PdfObjRef,
                    "Second Link child should be OBJR");
        }
    }
}
