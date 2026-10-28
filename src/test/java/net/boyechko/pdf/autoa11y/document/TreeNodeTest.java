// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeNodeTest {
    @Test
    void childrenCannotBeChangedThroughTheOriginalListOrTheAccessor() {
        var children = new ArrayList<>(List.of(TreeNode.leaf("H3")));
        var node = new TreeNode<>("LBody", children);

        children.clear();

        assertEquals(List.of(TreeNode.leaf("H3")), node.children());
        assertThrows(UnsupportedOperationException.class, () -> node.children().clear());
    }
}
