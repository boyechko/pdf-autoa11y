// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleTreeNotationTest {
    @Test
    void parsesEmptyElement() {
        List<TreeNode<String>> nodes = RoleTreeNotation.parse("Note[]");

        assertEquals(1, nodes.size());
        assertEquals("Note", nodes.get(0).value());
        assertTrue(nodes.get(0).children().isEmpty());
    }

    @Test
    void parsesNestedElements() {
        List<TreeNode<String>> nodes = RoleTreeNotation.parse("Reference[Lbl[]]");

        assertEquals(1, nodes.size());
        assertEquals("Reference", nodes.get(0).value());
        assertEquals(1, nodes.get(0).children().size());
        assertEquals("Lbl", nodes.get(0).children().get(0).value());
    }

    @Test
    void parsesSiblingElements() {
        List<TreeNode<String>> nodes = RoleTreeNotation.parse("Lbl[],LBody[]");

        assertEquals(2, nodes.size());
        assertEquals("Lbl", nodes.get(0).value());
        assertEquals("LBody", nodes.get(1).value());
    }

    @Test
    void parsesDeeplyNested() {
        List<TreeNode<String>> nodes = RoleTreeNotation.parse("L[LI[Lbl[],LBody[P[]]]]");

        assertEquals(1, nodes.size());
        TreeNode<String> l = nodes.get(0);
        assertEquals("L", l.value());
        TreeNode<String> li = l.children().get(0);
        assertEquals("LI", li.value());
        assertEquals(2, li.children().size());
        assertEquals("Lbl", li.children().get(0).value());
        assertEquals("LBody", li.children().get(1).value());
        assertEquals("P", li.children().get(1).children().get(0).value());
    }

    @Test
    void roundTripsWithFormatting() {
        String[] expressions = {
            "Note[]", "Reference[Lbl[]]", "L[LI[Lbl[], LBody[P[]]]]",
        };
        for (String expr : expressions) {
            List<TreeNode<String>> nodes = RoleTreeNotation.parse(expr);
            String result =
                    nodes.size() == 1
                            ? RoleTreeNotation.format(nodes.get(0))
                            : String.join(
                                    ", ", nodes.stream().map(RoleTreeNotation::format).toList());
            assertEquals(expr, result, "Round-trip failed for: " + expr);
        }
    }
}
