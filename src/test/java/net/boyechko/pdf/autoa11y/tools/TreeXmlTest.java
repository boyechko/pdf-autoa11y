// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.TreeNode;
import net.boyechko.pdf.autoa11y.document.TreeSnapshot;
import org.junit.jupiter.api.Test;

class TreeXmlTest {
    @Test
    void exportPreservesSpecialCharactersAndRolesThatAreNotXmlNames() {
        var snapshot =
                TreeNode.<TreeSnapshot.Value>branch(
                        new TreeSnapshot.Element(
                                "Custom / Role",
                                "Custom / Role",
                                -1,
                                new DocValue.Scribble("REVIEW & compare"),
                                null,
                                new DocValue.Scope("Column"),
                                2),
                        TreeNode.leaf(
                                new TreeSnapshot.MarkedContent(
                                        2,
                                        19,
                                        Set.of(Content.ContentKind.TEXT),
                                        "Full text: < & > \" ' —\nsecond line\twith tab\rand return",
                                        false)),
                        TreeNode.leaf(
                                new TreeSnapshot.ObjectReference(
                                        3,
                                        new DocValue.Link(100),
                                        new DocValue.Destination.GoToPage(7),
                                        new DocValue.Destination.Uri(
                                                "https://example.com/?a=1&b=2"))));

        assertEquals(snapshot, TreeXml.parse(TreeXml.format(snapshot)));
    }

    @Test
    void customRolesNamedLikeTheFormatsOwnTagsDoNotBorrowThem() {
        for (String role : List.of("StructureTree", "Content", "ObjectReference")) {
            var tree =
                    TreeNode.<TreeSnapshot.Value>branch(
                            new TreeSnapshot.Element(
                                    "Document", "Document", 1, null, null, null, 0),
                            TreeNode.leaf(
                                    new TreeSnapshot.Element(role, role, 2, null, null, null, 0)));

            assertTrue(TreeQuery.select(tree, "//*[@role and self::" + role + "]").isEmpty());
            assertEquals(1, TreeQuery.select(tree, "//*[@role='" + role + "']").size());
            assertEquals(tree, TreeXml.parse(TreeXml.format(tree)));
        }
    }

    @Test
    void exportedTreeRetainsItsDataAndDiagramWhenReadBack() throws Exception {
        try (var doc =
                new PdfDocument(
                        new PdfReader(
                                Path.of("src/test/resources/tagged_baseline.pdf").toString()))) {
            var snapshot = TreeSnapshot.capture(doc);

            String xml = TreeXml.format(snapshot);
            var restored = TreeXml.parse(xml);

            assertTrue(xml.contains("<Document"));
            assertTrue(xml.contains("<Content"));
            assertEquals(snapshot, restored);
            assertEquals(
                    TreeDiagram.toDetailedTreeString(snapshot, TreeDiagram.Style.BOX),
                    TreeDiagram.toDetailedTreeString(restored, TreeDiagram.Style.BOX));
        }
    }
}
