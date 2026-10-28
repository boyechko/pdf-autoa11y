// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import java.util.ArrayList;
import java.util.List;

/** Parses and formats role trees in bracket notation used by scribbled instructions. */
public final class RoleTreeNotation {
    private RoleTreeNotation() {}

    /** Parses bracket notation, e.g. {@code Reference[Lbl[], Span[]]}, into trees. */
    public static List<TreeNode<String>> parse(String expr) {
        List<TreeNode<String>> nodes = new ArrayList<>();
        int[] pos = {0};
        parseNodes(expr, pos, nodes);
        return nodes;
    }

    private static void parseNodes(String expr, int[] pos, List<TreeNode<String>> out) {
        while (pos[0] < expr.length()) {
            while (pos[0] < expr.length()
                    && (expr.charAt(pos[0]) == ' ' || expr.charAt(pos[0]) == ',')) {
                pos[0]++;
            }
            if (pos[0] >= expr.length() || expr.charAt(pos[0]) == ']') {
                break;
            }

            int nameStart = pos[0];
            while (pos[0] < expr.length()
                    && expr.charAt(pos[0]) != '['
                    && expr.charAt(pos[0]) != ']'
                    && expr.charAt(pos[0]) != ',') {
                pos[0]++;
            }
            String tagName = expr.substring(nameStart, pos[0]).trim();
            if (tagName.isEmpty()) {
                throw new IllegalArgumentException("Invalid tag expression at position " + pos[0]);
            }

            List<TreeNode<String>> children = new ArrayList<>();
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == '[') {
                pos[0]++;
                parseNodes(expr, pos, children);
                if (pos[0] < expr.length() && expr.charAt(pos[0]) == ']') {
                    pos[0]++;
                }
            }

            out.add(new TreeNode<>(tagName, children));
        }
    }

    /** Formats a role tree, e.g. {@code Document[L[LI[Lbl[], LBody[P[]]]]]}. */
    public static String format(TreeNode<String> node) {
        StringBuilder sb = new StringBuilder();
        appendNode(sb, node);
        return sb.toString();
    }

    private static void appendNode(StringBuilder sb, TreeNode<String> node) {
        sb.append(node.value()).append('[');
        for (int i = 0; i < node.children().size(); i++) {
            if (i > 0) sb.append(", ");
            appendNode(sb, node.children().get(i));
        }
        sb.append(']');
    }
}
