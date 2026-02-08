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
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import org.junit.jupiter.api.Test;

/** Tests for StructureTreeWalker and visitor infrastructure. */
class StructureTreeWalkerTest extends PdfTestBase {
    private Path createTestPdf() throws Exception {
        String filename = "document-with-two-paragraphs.pdf";
        OutputStream outputStream = testOutputStream(filename);
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream))) {
            pdfDoc.setTagged();
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("First paragraph"));
            doc.add(new Paragraph("Second paragraph"));
            doc.close();
        }
        return testOutputPath(filename);
    }

    @Test
    void walkerInvokesVisitorForEachElement() throws Exception {
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
                    public String description() {
                        return "Tracks visited roles";
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

        Path pdfFile = createTestPdf();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(trackingVisitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
        }

        assertTrue(visitedRoles.contains("Document"), "Should visit Document element");
        assertEquals(
                2,
                visitedRoles.stream().filter(r -> r.equals("P")).count(),
                "Should visit 2 P elements");
    }

    @Test
    void visitorContextProvidesCorrectPath() throws Exception {
        List<String> paths = new ArrayList<>();
        StructureTreeVisitor pathVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Path Visitor";
                    }

                    @Override
                    public String description() {
                        return "Tracks visited paths";
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

        Path pdfFile = createTestPdf();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(pathVisitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
        }

        assertTrue(
                paths.stream().anyMatch(p -> p.startsWith("/Document[")),
                "Should have Document path");
        assertTrue(paths.stream().anyMatch(p -> p.contains(".P[")), "Should have nested P path");
    }

    @Test
    void multipleVisitorsReceiveSameContext() throws Exception {
        Path pdfFile = createTestPdf();

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
                    public String description() {
                        return "Tracks visited indices for visitor 1";
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
                    public String description() {
                        return "Tracks visited indices for visitor 2";
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

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(visitor1);
            walker.addVisitor(visitor2);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
        }

        // Both visitors should see the same elements in the same order
        assertEquals(visitor1Indices, visitor2Indices, "Both visitors should see same indices");
    }

    @Test
    void visitorContextProvidesChildRoles() throws Exception {
        Path pdfFile = createTestPdf();

        List<String> documentChildRoles = new ArrayList<>();
        StructureTreeVisitor childRoleVisitor =
                new StructureTreeVisitor() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Child Role Visitor";
                    }

                    @Override
                    public String description() {
                        return "Tracks visited child roles";
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

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructureTreeWalker walker = new StructureTreeWalker(TagSchema.loadDefault());
            walker.addVisitor(childRoleVisitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocumentContext(pdfDoc));
        }

        assertEquals(2, documentChildRoles.size(), "Document should have 2 children");
        assertTrue(
                documentChildRoles.stream().allMatch(r -> r.equals("P")),
                "Children should be P elements");
    }
}
