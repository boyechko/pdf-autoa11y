// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.boyechko.pdf.autoa11y.document.TreeNode;
import net.boyechko.pdf.autoa11y.document.TreeSnapshot;
import org.junit.jupiter.api.Test;

class TreeQueryTest {
    private static final String TREE =
            """
            <StructureTree version="1" root="element">
              <Document role="Document" id="1">
                <Art role="Art" id="4291">
                  <L role="L" id="10">
                    <LI role="LI" id="11">
                      <LBody role="LBody" id="12">
                        <H3 role="H3" id="13"/>
                        <H4 role="H4" id="14"/>
                        <P role="P" id="15" page="1">
                          <Content page="1" mcid="0" kinds="text">A long program description whose distinguishing phrase is beyond the short diagram excerpt: Health Studies</Content>
                        </P>
                      </LBody>
                    </LI>
                  </L>
                  <L role="L" id="20">
                    <LI role="LI" id="21">
                      <LBody role="LBody" id="22">
                        <P role="P" id="23"><H5 role="H5" id="24"/></P>
                      </LBody>
                    </LI>
                  </L>
                </Art>
                <Art role="Art" id="4292">
                  <L role="L" id="30">
                    <LI role="LI" id="31">
                      <LBody role="LBody" id="32"><H5 role="H5" id="33"/></LBody>
                    </LI>
                  </L>
                </Art>
              </Document>
            </StructureTree>
            """;

    @Test
    void selectsEachListOnceWithinTheArticleAndRespectsDirectChildren() {
        var matches =
                TreeQuery.select(
                        TreeXml.parse(TREE),
                        "//Art[@id='4291']//L[LI/LBody/*[self::H3 or self::H4 or self::H5]]");

        assertEquals(
                List.of(10),
                matches.stream()
                        .map(node -> ((TreeSnapshot.Element) node.value()).objNum())
                        .toList());
    }

    @Test
    void searchesFullTextBeyondTheDiagramExcerpt() {
        var matches =
                TreeQuery.select(
                        TreeXml.parse(TREE), "//L[.//Content[contains(., 'Health Studies')]]");

        assertEquals(1, matches.size());
        assertEquals(10, ((TreeSnapshot.Element) matches.getFirst().value()).objNum());
        assertFalse(
                TreeDiagram.toDetailedTreeString(matches.getFirst(), TreeDiagram.Style.BOX)
                        .contains("Health Studies"));
    }

    @Test
    void descendantQueriesCanFindHeadingsInsideWrappers() {
        var matches =
                TreeQuery.select(
                        TreeXml.parse(TREE),
                        "//Art[@id='4291']//L[LI/LBody//*[self::H3 or self::H4 or self::H5]]");

        assertEquals(
                List.of(10, 20),
                matches.stream()
                        .map(node -> ((TreeSnapshot.Element) node.value()).objNum())
                        .toList());
    }

    @Test
    void matchesInsideAnotherMatchAppearOnlyWithinIt() {
        var matches = TreeQuery.select(TreeXml.parse(TREE), "//Art[@id='4291'] | //L");

        assertEquals(
                List.of(4291, 30),
                matches.stream()
                        .map(node -> ((TreeSnapshot.Element) node.value()).objNum())
                        .toList());
    }

    @Test
    void queryResultsExportedAsAContainerCanBeReadBackAndQueriedAgain() {
        var matches = TreeQuery.select(TreeXml.parse(TREE), "//Art");
        String xml = TreeXml.format(new TreeNode<>(new TreeSnapshot.Root(), matches));

        var restored = TreeXml.parse(xml);

        assertInstanceOf(TreeSnapshot.Root.class, restored.value());
        assertEquals(matches, restored.children());
        assertEquals(
                List.of(10, 20, 30),
                TreeQuery.select(restored, "//L").stream()
                        .map(node -> ((TreeSnapshot.Element) node.value()).objNum())
                        .toList());
    }

    @Test
    void unmatchedQueryReturnsNoSubtrees() {
        assertTrue(TreeQuery.select(TreeXml.parse(TREE), "//H6").isEmpty());
    }

    @Test
    void rejectsMalformedExpressionsAndResultsThatAreNotStructureElements() {
        var tree = TreeXml.parse(TREE);

        for (String expression :
                List.of(
                        "//L[",
                        "//L/@id",
                        "count(//L)",
                        "//Content",
                        "/",
                        "/*",
                        "/StructureTree")) {
            assertThrows(IllegalArgumentException.class, () -> TreeQuery.select(tree, expression));
        }
    }
}
