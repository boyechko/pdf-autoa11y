/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.boyechko.pdf.autoa11y.document.TreeDiagram.Node;
import org.junit.jupiter.api.Test;

class TreeDiagramTest {

    @Test
    void parsesEmptyElement() {
        List<Node<String>> nodes = Node.fromString("Note[]");

        assertEquals(1, nodes.size());
        assertEquals("Note", nodes.get(0).value());
        assertTrue(nodes.get(0).children().isEmpty());
    }

    @Test
    void parsesNestedElements() {
        List<Node<String>> nodes = Node.fromString("Reference[Lbl[]]");

        assertEquals(1, nodes.size());
        assertEquals("Reference", nodes.get(0).value());
        assertEquals(1, nodes.get(0).children().size());
        assertEquals("Lbl", nodes.get(0).children().get(0).value());
    }

    @Test
    void parsesSiblingElements() {
        List<Node<String>> nodes = Node.fromString("Lbl[],LBody[]");

        assertEquals(2, nodes.size());
        assertEquals("Lbl", nodes.get(0).value());
        assertEquals("LBody", nodes.get(1).value());
    }

    @Test
    void parsesDeeplyNested() {
        List<Node<String>> nodes = Node.fromString("L[LI[Lbl[],LBody[P[]]]]");

        assertEquals(1, nodes.size());
        Node<String> l = nodes.get(0);
        assertEquals("L", l.value());
        Node<String> li = l.children().get(0);
        assertEquals("LI", li.value());
        assertEquals(2, li.children().size());
        assertEquals("Lbl", li.children().get(0).value());
        assertEquals("LBody", li.children().get(1).value());
        assertEquals("P", li.children().get(1).children().get(0).value());
    }

    @Test
    void roundTripsWithToString() {
        String[] expressions = {
            "Note[]", "Reference[Lbl[]]", "L[LI[Lbl[], LBody[P[]]]]",
        };
        for (String expr : expressions) {
            List<Node<String>> nodes = Node.fromString(expr);
            String result =
                    nodes.size() == 1
                            ? nodes.get(0).toString()
                            : String.join(", ", nodes.stream().map(Node::toString).toList());
            assertEquals(expr, result, "Round-trip failed for: " + expr);
        }
    }
}
