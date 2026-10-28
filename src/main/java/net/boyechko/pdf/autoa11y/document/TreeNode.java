// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import java.util.List;

/** A tree node with a value and an immutable, ordered list of children. */
public record TreeNode<T>(T value, List<TreeNode<T>> children) {
    public TreeNode {
        children = List.copyOf(children);
    }

    /** Creates a leaf node (no children). */
    public static <T> TreeNode<T> leaf(T value) {
        return new TreeNode<>(value, List.of());
    }

    /** Creates a node with children in the supplied order. */
    @SafeVarargs
    public static <T> TreeNode<T> branch(T value, TreeNode<T>... children) {
        return new TreeNode<>(value, List.of(children));
    }
}
