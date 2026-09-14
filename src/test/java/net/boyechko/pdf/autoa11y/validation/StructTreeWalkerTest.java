// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.junit.jupiter.api.Test;

/** Tests for StructTreeWalker and visitor infrastructure. */
class StructTreeWalkerTest extends PdfTestBase {
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
        StructTreeCheck trackingVisitor =
                new StructTreeCheck() {
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
                    public boolean enterElement(StructTreeContext ctx) {
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
            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(trackingVisitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
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
        StructTreeCheck pathVisitor =
                new StructTreeCheck() {
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
                    public boolean enterElement(StructTreeContext ctx) {
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
            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(pathVisitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
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

        StructTreeCheck visitor1 =
                new StructTreeCheck() {
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
                    public boolean enterElement(StructTreeContext ctx) {
                        visitor1Indices.add(ctx.globalIndex());
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        StructTreeCheck visitor2 =
                new StructTreeCheck() {
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
                    public boolean enterElement(StructTreeContext ctx) {
                        visitor2Indices.add(ctx.globalIndex());
                        return true;
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFile.toString()))) {
            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(visitor1);
            walker.addVisitor(visitor2);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
        }

        // Both visitors should see the same elements in the same order
        assertEquals(visitor1Indices, visitor2Indices, "Both visitors should see same indices");
    }

    @Test
    void verifiedSubtreeIsHiddenFromVisitors() throws Exception {
        List<String> entered = new ArrayList<>();
        List<String> left = new ArrayList<>();
        StructTreeCheck trackingVisitor =
                new StructTreeCheck() {
                    private final IssueList issues = new IssueList();

                    @Override
                    public String name() {
                        return "Tracking Visitor";
                    }

                    @Override
                    public String description() {
                        return "Tracks entered and left roles";
                    }

                    @Override
                    public boolean enterElement(StructTreeContext ctx) {
                        entered.add(ctx.role());
                        return true;
                    }

                    @Override
                    public void leaveElement(StructTreeContext ctx) {
                        left.add(ctx.role());
                    }

                    @Override
                    public IssueList getIssues() {
                        return issues;
                    }
                };

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            var root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem verifiedArt = new PdfStructElem(pdfDoc, PdfName.Art);
            document.addKid(verifiedArt);
            PdfStructElem pInsideArt = new PdfStructElem(pdfDoc, PdfName.P);
            verifiedArt.addKid(pInsideArt);
            StructTree.setScribble(verifiedArt, StructTree.SCRIBBLE_VERIFIED_TOKEN);

            PdfStructElem siblingP = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(siblingP);

            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(trackingVisitor);
            walker.walk(root, new DocContext(pdfDoc));
        }

        assertEquals(List.of("Document", "P"), entered, "verified Art and its P must be skipped");
        assertEquals(List.of("P", "Document"), left, "leaveElement must also skip the subtree");
    }

    @Test
    void visitorContextProvidesChildRoles() throws Exception {
        Path pdfFile = createTestPdf();

        List<String> documentChildRoles = new ArrayList<>();
        StructTreeCheck childRoleVisitor =
                new StructTreeCheck() {
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
                    public boolean enterElement(StructTreeContext ctx) {
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
            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(childRoleVisitor);
            walker.walk(pdfDoc.getStructTreeRoot(), new DocContext(pdfDoc));
        }

        assertEquals(2, documentChildRoles.size(), "Document should have 2 children");
        assertTrue(
                documentChildRoles.stream().allMatch(r -> r.equals("P")),
                "Children should be P elements");
    }

    @Test
    void scopedWalkVisitsOnlyTheTargetSubtree() throws Exception {
        RoleTracker tracker = new RoleTracker();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            var root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem scopedArt = new PdfStructElem(pdfDoc, PdfName.Art);
            document.addKid(scopedArt);
            PdfStructElem pInsideArt = new PdfStructElem(pdfDoc, PdfName.P);
            scopedArt.addKid(pInsideArt);

            PdfStructElem siblingP = new PdfStructElem(pdfDoc, PdfName.P);
            document.addKid(siblingP);

            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(tracker);
            walker.walk(root, new DocContext(pdfDoc, StructTree.objNum(scopedArt)));
        }

        assertEquals(
                List.of("Art", "P"),
                tracker.entered,
                "only the scoped Art and its descendants must be visited");
        assertEquals(List.of("P", "Art"), tracker.left, "leaveElement must also stay in scope");
    }

    @Test
    void scopeOnMissingObjectNumberVisitsNothing() throws Exception {
        RoleTracker tracker = new RoleTracker();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            var root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P));

            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(tracker);
            walker.walk(root, new DocContext(pdfDoc, 999999));
        }

        assertTrue(tracker.entered.isEmpty(), "an unresolvable scope must visit nothing");
    }

    @Test
    void verifiedSubtreeInsideScopeIsStillSkipped() throws Exception {
        RoleTracker tracker = new RoleTracker();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            var root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);

            PdfStructElem scopedArt = new PdfStructElem(pdfDoc, PdfName.Art);
            document.addKid(scopedArt);
            PdfStructElem verifiedSect = new PdfStructElem(pdfDoc, PdfName.Sect);
            scopedArt.addKid(verifiedSect);
            verifiedSect.addKid(new PdfStructElem(pdfDoc, PdfName.P));
            StructTree.setScribble(verifiedSect, StructTree.SCRIBBLE_VERIFIED_TOKEN);

            StructTreeWalker walker = new StructTreeWalker();
            walker.addVisitor(tracker);
            walker.walk(root, new DocContext(pdfDoc, StructTree.objNum(scopedArt)));
        }

        assertEquals(List.of("Art"), tracker.entered, "verified subtree must be skipped in scope");
    }

    /** Records the roles entered and left, in traversal order. */
    private static class RoleTracker extends StructTreeCheck {
        final List<String> entered = new ArrayList<>();
        final List<String> left = new ArrayList<>();
        private final IssueList issues = new IssueList();

        @Override
        public String name() {
            return "Role Tracker";
        }

        @Override
        public String description() {
            return "Tracks entered and left roles";
        }

        @Override
        public boolean enterElement(StructTreeContext ctx) {
            entered.add(ctx.role());
            return true;
        }

        @Override
        public void leaveElement(StructTreeContext ctx) {
            left.add(ctx.role());
        }

        @Override
        public IssueList getIssues() {
            return issues;
        }
    }
}
