// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathNodes;
import net.boyechko.pdf.autoa11y.document.TreeNode;
import net.boyechko.pdf.autoa11y.document.TreeSnapshot;
import org.w3c.dom.Node;

/** Selects structure subtrees with XPath 1.0 expressions. */
public final class TreeQuery {
    private TreeQuery() {}

    /**
     * Returns the outermost matching structure elements in document order; a match inside another
     * match appears only within its subtree.
     */
    public static List<TreeNode<TreeSnapshot.Value>> select(
            TreeNode<TreeSnapshot.Value> tree, String expression) {
        try {
            var factory = XPathFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var xpath = factory.newXPath().compile(expression);
            var result = xpath.evaluateExpression(TreeXml.toDocument(tree));
            if (result.type() != XPathEvaluationResult.XPathResultType.NODESET) {
                throw new IllegalArgumentException(
                        "Tree queries must select structure elements, not scalar values");
            }
            var nodes = (XPathNodes) result.value();
            List<TreeNode<TreeSnapshot.Value>> matches = new ArrayList<>();
            Node outermost = null;
            for (var node : nodes) {
                var snapshot = TreeXml.snapshotOf(node);
                if (snapshot == null || !(snapshot.value() instanceof TreeSnapshot.Element)) {
                    throw new IllegalArgumentException(
                            "Tree queries must select structure elements, not " + describe(node));
                }
                if (outermost != null && contains(outermost, node)) continue;
                outermost = node;
                matches.add(snapshot);
            }
            return List.copyOf(matches);
        } catch (XPathExpressionException | XPathFactoryConfigurationException e) {
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new IllegalArgumentException("Invalid tree XPath: " + detail, e);
        }
    }

    /** Names what a non-element selection picked, for the rejection message. */
    private static String describe(Node node) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) return "attributes";
        if (node.getNodeType() == Node.DOCUMENT_NODE
                || node == node.getOwnerDocument().getDocumentElement()) {
            return "the StructureTree wrapper";
        }
        return "content";
    }

    /** Returns whether {@code node} lies inside {@code ancestor}'s subtree. */
    private static boolean contains(Node ancestor, Node node) {
        return (ancestor.compareDocumentPosition(node) & Node.DOCUMENT_POSITION_CONTAINED_BY) != 0;
    }
}
