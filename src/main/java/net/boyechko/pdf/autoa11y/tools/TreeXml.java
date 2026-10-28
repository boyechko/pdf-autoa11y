// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.TreeNode;
import net.boyechko.pdf.autoa11y.document.TreeSnapshot;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Serializes structure snapshots as XML with role names suitable for XPath queries. */
public final class TreeXml {
    private static final String SNAPSHOT_KEY = TreeXml.class.getName();
    private static final Set<String> RESERVED_TAGS =
            Set.of("StructureTree", "Content", "ObjectReference");

    private TreeXml() {}

    /** Exports a snapshot with full text, labels, page numbers, and ordered children. */
    public static String toString(TreeNode<TreeSnapshot.Value> tree) {
        try {
            var factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            var output = new StringWriter();
            transformer.transform(new DOMSource(toDocument(tree)), new StreamResult(output));
            return output.toString();
        } catch (TransformerException e) {
            throw new IllegalStateException("Cannot configure the XML serializer", e);
        }
    }

    /** Reads a version-1 snapshot export without loading external entities or DTDs. */
    public static TreeNode<TreeSnapshot.Value> fromString(String xml) {
        try {
            var builder = documentFactory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var document = builder.parse(new InputSource(new StringReader(xml)));
            var wrapper = document.getDocumentElement();
            if (!"StructureTree".equals(wrapper.getTagName())
                    || !"1".equals(wrapper.getAttribute("version"))) {
                throw new IllegalArgumentException("Expected a version-1 StructureTree export");
            }
            var children = readChildren(wrapper);
            return switch (wrapper.getAttribute("root")) {
                case "element" -> {
                    if (children.size() != 1
                            || !(children.getFirst().value() instanceof TreeSnapshot.Element)) {
                        throw new IllegalArgumentException("Expected one root element");
                    }
                    yield children.getFirst();
                }
                case "container" -> new TreeNode<>(new TreeSnapshot.Root(), children);
                default -> throw new IllegalArgumentException("Unknown structure tree root kind");
            };
        } catch (SAXException | IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Cannot read structure tree XML: " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot configure the XML parser", e);
        }
    }

    // The DOM and its snapshot references form the internal seam used by TreeQuery.
    static Document toDocument(TreeNode<TreeSnapshot.Value> tree) {
        try {
            var document = documentFactory().newDocumentBuilder().newDocument();
            var wrapper = document.createElement("StructureTree");
            wrapper.setAttribute("version", "1");
            boolean container = tree.value() instanceof TreeSnapshot.Root;
            wrapper.setAttribute("root", container ? "container" : "element");
            document.appendChild(wrapper);
            if (container) {
                for (var child : tree.children()) appendNode(wrapper, child);
            } else {
                appendNode(wrapper, tree);
            }
            return document;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot configure the XML parser", e);
        }
    }

    @SuppressWarnings("unchecked")
    static TreeNode<TreeSnapshot.Value> snapshotOf(Node node) {
        return (TreeNode<TreeSnapshot.Value>) node.getUserData(SNAPSHOT_KEY);
    }

    private static DocumentBuilderFactory documentFactory() throws ParserConfigurationException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    /** Returns the role as a tag, or StructElem if it is not an XML name or is the format's own. */
    private static String tagOf(String role) {
        return role.matches("[A-Za-z_][A-Za-z0-9_.-]*") && !RESERVED_TAGS.contains(role)
                ? role
                : "StructElem";
    }

    private static void appendNode(Element parent, TreeNode<TreeSnapshot.Value> tree) {
        var document = parent.getOwnerDocument();
        Element node;
        switch (tree.value()) {
            case TreeSnapshot.Element element -> {
                String role = element.mappedRole();
                node = document.createElement(tagOf(role));
                attribute(node, "role", role);
                if (!element.role().equals(role)) attribute(node, "rawRole", element.role());
                if (element.objNum() >= 0) attribute(node, "id", element.objNum());
                if (element.scribble() != null)
                    attribute(node, "scribble", element.scribble().rawValue());
                if (element.title() != null) attribute(node, "title", element.title().value());
                if (element.scope() != null) attribute(node, "scope", element.scope().value());
            }
            case TreeSnapshot.MarkedContent content -> {
                node = document.createElement("Content");
                attribute(node, "mcid", content.mcid());
                attribute(
                        node,
                        "kinds",
                        Arrays.stream(Content.ContentKind.values())
                                .filter(content.kinds()::contains)
                                .map(kind -> kind.name().toLowerCase(Locale.ROOT))
                                .collect(Collectors.joining(" ")));
                if (content.orphaned()) attribute(node, "orphaned", true);
                node.setTextContent(xmlText(content.text()));
            }
            case TreeSnapshot.ObjectReference ref -> {
                node = document.createElement("ObjectReference");
                writeAnnotation(node, ref.annotation());
                writeDestination(node, "destination", ref.destination());
                writeDestination(node, "originalUri", ref.originalUri());
            }
            case TreeSnapshot.Root ignored ->
                    throw new IllegalArgumentException("Nested synthetic root in snapshot");
        }
        if (tree.value().page() > 0) attribute(node, "page", tree.value().page());
        node.setUserData(SNAPSHOT_KEY, tree, null);
        parent.appendChild(node);
        for (var child : tree.children()) appendNode(node, child);
    }

    private static void writeAnnotation(Element node, DocValue annotation) {
        if (annotation == null) return;
        switch (annotation) {
            case DocValue.Link link -> {
                attribute(node, "subtype", "link");
                attribute(node, "id", link.objNum());
            }
            case DocValue.Widget widget -> {
                attribute(node, "subtype", "widget");
                attribute(node, "id", widget.objNum());
            }
            case DocValue.AnnotOther other -> {
                attribute(node, "subtype", other.subtype());
                attribute(node, "id", other.objNum());
            }
            case DocValue.ObjNum number -> attribute(node, "id", number.value());
            default -> throw new IllegalArgumentException("Not an annotation value: " + annotation);
        }
    }

    private static void writeDestination(
            Element node, String name, DocValue.Destination destination) {
        if (destination == null) return;
        switch (destination) {
            case DocValue.Destination.Uri uri -> {
                attribute(node, name + "Type", "uri");
                attribute(node, name, uri.url());
            }
            case DocValue.Destination.GoToPage page -> {
                attribute(node, name + "Type", "page");
                attribute(node, name, page.pageNum());
            }
            case DocValue.Destination.GoToNamed named -> {
                attribute(node, name + "Type", "named");
                attribute(node, name, named.name());
            }
        }
    }

    private static void attribute(Element node, String name, Object value) {
        node.setAttribute(name, xmlText(value.toString()));
    }

    /** Replaces characters XML 1.0 cannot represent with the Unicode replacement character. */
    private static String xmlText(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints()
                .forEach(
                        code ->
                                result.appendCodePoint(
                                        code == 9
                                                        || code == 10
                                                        || code == 13
                                                        || code >= 32 && code <= 0xD7FF
                                                        || code >= 0xE000 && code <= 0xFFFD
                                                        || code >= 0x10000 && code <= 0x10FFFF
                                                ? code
                                                : 0xFFFD));
        return result.toString();
    }

    private static List<TreeNode<TreeSnapshot.Value>> readChildren(Element parent) {
        List<TreeNode<TreeSnapshot.Value>> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) children.add(readNode(element));
        }
        return children;
    }

    private static TreeNode<TreeSnapshot.Value> readNode(Element node) {
        int page = number(node, "page", 0);
        TreeSnapshot.Value value;
        if (node.hasAttribute("role")) {
            String role = node.getAttribute("role");
            String rawRole = node.hasAttribute("rawRole") ? node.getAttribute("rawRole") : role;
            String scribble = node.getAttribute("scribble");
            if (node.hasAttribute("scribble") && !scribble.startsWith(StructTree.SCRIBBLE_PREFIX)) {
                throw new IllegalArgumentException("Scribble must begin with __");
            }
            value =
                    new TreeSnapshot.Element(
                            rawRole,
                            role,
                            number(node, "id", -1),
                            node.hasAttribute("scribble")
                                    ? new DocValue.Scribble(
                                            scribble.substring(StructTree.SCRIBBLE_PREFIX.length()))
                                    : null,
                            node.hasAttribute("title")
                                    ? new DocValue.Title(node.getAttribute("title"))
                                    : null,
                            node.hasAttribute("scope")
                                    ? new DocValue.Scope(node.getAttribute("scope"))
                                    : null,
                            page);
        } else if ("Content".equals(node.getTagName())) {
            Set<Content.ContentKind> kinds =
                    Arrays.stream(node.getAttribute("kinds").split("\\s+"))
                            .filter(kind -> !kind.isEmpty())
                            .map(kind -> Content.ContentKind.valueOf(kind.toUpperCase(Locale.ROOT)))
                            .collect(Collectors.toSet());
            value =
                    new TreeSnapshot.MarkedContent(
                            page,
                            number(node, "mcid", -1),
                            kinds,
                            node.getTextContent(),
                            Boolean.parseBoolean(node.getAttribute("orphaned")));
        } else if ("ObjectReference".equals(node.getTagName())) {
            value =
                    new TreeSnapshot.ObjectReference(
                            page,
                            readAnnotation(node),
                            readDestination(node, "destination"),
                            readDestination(node, "originalUri"));
        } else {
            throw new IllegalArgumentException("Unknown snapshot node: " + node.getTagName());
        }
        return new TreeNode<>(value, readChildren(node));
    }

    private static DocValue readAnnotation(Element node) {
        int id = number(node, "id", -1);
        return switch (node.getAttribute("subtype")) {
            case "link" -> new DocValue.Link(id);
            case "widget" -> new DocValue.Widget(id);
            case "" -> node.hasAttribute("id") ? new DocValue.ObjNum(id) : null;
            default -> new DocValue.AnnotOther(node.getAttribute("subtype"), id);
        };
    }

    private static DocValue.Destination readDestination(Element node, String name) {
        return switch (node.getAttribute(name + "Type")) {
            case "" -> null;
            case "uri" -> new DocValue.Destination.Uri(node.getAttribute(name));
            case "page" -> new DocValue.Destination.GoToPage(number(node, name, 0));
            case "named" -> new DocValue.Destination.GoToNamed(node.getAttribute(name));
            default -> throw new IllegalArgumentException("Unknown destination type for " + name);
        };
    }

    private static int number(Element node, String attribute, int defaultValue) {
        return node.hasAttribute(attribute)
                ? Integer.parseInt(node.getAttribute(attribute))
                : defaultValue;
    }
}
