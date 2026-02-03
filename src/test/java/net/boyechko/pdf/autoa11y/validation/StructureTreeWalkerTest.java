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
package net.boyechko.pdf.autoa11y.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.junit.jupiter.api.Test;

/** Tests for StructureTreeWalker and visitor infrastructure. */
class StructureTreeWalkerTest extends PdfTestBase {

    @Test
    void walkerInvokesVisitorForEachElement() throws Exception {
        // Create a simple tagged PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("First paragraph"));
            doc.add(new Paragraph("Second paragraph"));
            doc.close();
        }

        // Track visited elements
        List<String> visitedRoles = new ArrayList<>();
        StructureTreeVisitor trackingVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Tracking Visitor";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        visitedRoles.add(ctx.role());
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        // Walk the tree
        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            DocumentContext docCtx = new DocumentContext(pdfDoc);
            TagSchema schema = TagSchema.loadDefault();

            StructureTreeWalker walker = new StructureTreeWalker(schema);
            walker.addVisitor(trackingVisitor);
            walker.walk(root, docCtx);
        }

        // Should have visited Document and two P elements
        assertTrue(visitedRoles.contains("Document"), "Should visit Document element");
        assertEquals(
                2,
                visitedRoles.stream().filter(r -> r.equals("P")).count(),
                "Should visit 2 P elements");
    }

    @Test
    void visitorContextProvidesCorrectPath() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("Test"));
            doc.close();
        }

        List<String> paths = new ArrayList<>();
        StructureTreeVisitor pathVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Path Visitor";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        paths.add(ctx.path());
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            DocumentContext docCtx = new DocumentContext(pdfDoc);
            TagSchema schema = TagSchema.loadDefault();

            StructureTreeWalker walker = new StructureTreeWalker(schema);
            walker.addVisitor(pathVisitor);
            walker.walk(root, docCtx);
        }

        // Paths should be hierarchical with indices
        assertTrue(
                paths.stream().anyMatch(p -> p.startsWith("/Document[")),
                "Should have Document path");
        assertTrue(paths.stream().anyMatch(p -> p.contains(".P[")), "Should have nested P path");
    }

    @Test
    void figureWithTextVisitorVisitsFigureElements() throws Exception {
        // Create a PDF with a Figure element
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);

            // Create a paragraph tagged as Figure
            Paragraph figureWithText = new Paragraph("This is actually text, not a figure image");
            figureWithText.getAccessibilityProperties().setRole("Figure");
            doc.add(figureWithText);

            doc.add(new Paragraph("Normal paragraph"));
            doc.close();
        }

        // Track what roles the visitor sees
        List<String> visitedRoles = new ArrayList<>();
        List<Integer> figurePageNumbers = new ArrayList<>();

        StructureTreeVisitor trackingVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Figure Tracking Visitor";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        visitedRoles.add(ctx.role());
                        if (ctx.hasRole("Figure")) {
                            figurePageNumbers.add(ctx.getPageNumber());
                        }
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            DocumentContext docCtx = new DocumentContext(pdfDoc);
            TagSchema schema = TagSchema.loadDefault();

            StructureTreeWalker walker = new StructureTreeWalker(schema);
            walker.addVisitor(trackingVisitor);
            walker.walk(root, docCtx);
        }

        // Should have visited the Figure element
        assertTrue(visitedRoles.contains("Figure"), "Should visit Figure element");
        assertEquals(1, figurePageNumbers.size(), "Should find 1 Figure");
        assertEquals(1, figurePageNumbers.get(0), "Figure should be on page 1");
    }

    @Test
    void figureWithTextVisitorCreatesIssuesForFigureElements() throws Exception {
        // Create a custom visitor that always creates an issue for Figures
        // (simulates what FigureWithTextVisitor does when it finds text)
        StructureTreeVisitor issueCreatingVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Issue Creating Visitor";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        if (ctx.hasRole("Figure")) {
                            issues.add(
                                    new net.boyechko.pdf.autoa11y.issues.Issue(
                                            IssueType.FIGURE_WITH_TEXT,
                                            net.boyechko.pdf.autoa11y.issues.IssueSeverity.WARNING,
                                            new net.boyechko.pdf.autoa11y.issues.IssueLocation(
                                                    ctx.node()),
                                            "Test issue for Figure"));
                        }
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            Paragraph fig = new Paragraph("Figure content");
            fig.getAccessibilityProperties().setRole("Figure");
            doc.add(fig);
            doc.close();
        }

        IssueList issues;
        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            DocumentContext docCtx = new DocumentContext(pdfDoc);
            TagSchema schema = TagSchema.loadDefault();

            StructureTreeWalker walker = new StructureTreeWalker(schema);
            walker.addVisitor(issueCreatingVisitor);
            issues = walker.walk(root, docCtx);
        }

        assertEquals(1, issues.size(), "Should create 1 issue for Figure");
        assertEquals(IssueType.FIGURE_WITH_TEXT, issues.get(0).type());
    }

    @Test
    void multipleVisitorsReceiveSameContext() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("Test"));
            doc.close();
        }

        List<Integer> visitor1Indices = new ArrayList<>();
        List<Integer> visitor2Indices = new ArrayList<>();

        StructureTreeVisitor visitor1 =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Visitor 1";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        visitor1Indices.add(ctx.globalIndex());
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        StructureTreeVisitor visitor2 =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Visitor 2";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        visitor2Indices.add(ctx.globalIndex());
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            DocumentContext docCtx = new DocumentContext(pdfDoc);
            TagSchema schema = TagSchema.loadDefault();

            StructureTreeWalker walker = new StructureTreeWalker(schema);
            walker.addVisitor(visitor1);
            walker.addVisitor(visitor2);
            walker.walk(root, docCtx);
        }

        // Both visitors should see the same elements in the same order
        assertEquals(visitor1Indices, visitor2Indices, "Both visitors should see same indices");
    }

    @Test
    void visitorContextProvidesChildRoles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("First"));
            doc.add(new Paragraph("Second"));
            doc.close();
        }

        List<String> documentChildRoles = new ArrayList<>();
        StructureTreeVisitor childRoleVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Child Role Visitor";
                    }

                    @Override
                    public boolean enterElement(VisitorContext ctx) {
                        if (ctx.hasRole("Document")) {
                            documentChildRoles.addAll(ctx.childRoles());
                        }
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        try (PdfDocument pdfDoc =
                new PdfDocument(new PdfReader(new ByteArrayInputStream(baos.toByteArray())))) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            DocumentContext docCtx = new DocumentContext(pdfDoc);
            TagSchema schema = TagSchema.loadDefault();

            StructureTreeWalker walker = new StructureTreeWalker(schema);
            walker.addVisitor(childRoleVisitor);
            walker.walk(root, docCtx);
        }

        // Document should have P children
        assertEquals(2, documentChildRoles.size(), "Document should have 2 children");
        assertTrue(
                documentChildRoles.stream().allMatch(r -> r.equals("P")),
                "Children should be P elements");
    }
}
