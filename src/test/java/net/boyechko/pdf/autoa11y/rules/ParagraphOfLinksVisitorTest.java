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

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.document.StructureTree.Node;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.junit.jupiter.api.Test;

public class ParagraphOfLinksVisitorTest extends PdfTestBase {
    private Path createTestPdf() throws Exception {
        return createTestPdf(
                (pdfDoc, layoutDoc) -> {
                    Paragraph p = new Paragraph();
                    for (int i = 0; i < 5; i++) {
                        p.add(new Link("Link " + i, PdfAction.createURI("https://uw.edu")));
                    }
                    layoutDoc.add(p);
                });
    }

    @Test
    void detectsAndFixesParagraphOfLinks() throws Exception {
        ParagraphOfLinksVisitor visitor = new ParagraphOfLinksVisitor();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            Document layoutDoc = new Document(pdfDoc);
            Paragraph p = new Paragraph();
            for (int i = 0; i < 5; i++) {
                Link link = new Link("Link " + i, PdfAction.createURI("https://uw.edu"));
                p.add(link);
            }
            layoutDoc.add(p);

            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(visitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));

            assertEquals(1, visitor.getIssues().size(), "Should have 1 issue");
            Issue issue = visitor.getIssues().get(0);
            assertEquals(
                    IssueType.PARAGRAPH_OF_LINKS,
                    issue.type(),
                    "Issue type should be PARAGRAPH_OF_LINKS");

            issue.fix().apply(new DocumentContext(pdfDoc));

            Node<String> roleTree =
                    StructureTree.toRoleTree(
                            StructureTree.findDocument(pdfDoc.getStructTreeRoot()));
            assertEquals(
                    "Document[L[LI[LBody[Link]], LI[LBody[Link]], LI[LBody[Link]], LI[LBody[Link]], LI[LBody[Link]]]]",
                    roleTree.toString());
            layoutDoc.close();
        }
    }

    @Test
    void ignoresParagraphsWithIntermixedLinks() throws Exception {
        ParagraphOfLinksVisitor visitor = new ParagraphOfLinksVisitor();

        createTestPdf(
                (pdfDoc, layoutDoc) -> {
                    Paragraph p = new Paragraph();
                    for (int i = 0; i < 5; i++) {
                        p.add(new Link("Link " + i, PdfAction.createURI("https://uw.edu")));
                        p.add(new Text("Text between links"));
                    }
                    layoutDoc.add(p);
                    assertEquals(
                            "Document[P[Link,Span,Link,Span,Link,Span,Link,Span,Link,Span]]",
                            StructureTree.toRoleTreeString(
                                    StructureTree.findDocument(pdfDoc.getStructTreeRoot())));

                    StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
                    walker.addVisitor(visitor);
                    walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
                    assertEquals(0, visitor.getIssues().size(), "Should have 0 issues");
                });
    }
}
