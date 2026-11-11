// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures an ordered structure tree without retaining live PDF objects. */
public final class TreeSnapshot {
    private static final Logger logger = LoggerFactory.getLogger(TreeSnapshot.class);

    private TreeSnapshot() {}

    /** Snapshot values distinguish structural elements from their content children. */
    public sealed interface Value {
        /** Returns the first content page, or zero when unavailable. */
        int page();
    }

    /** A synthetic container for a structure-tree root with no Document element. */
    public record Root() implements Value {
        @Override
        public int page() {
            return 0;
        }
    }

    /** An element's original and mapped roles, labels, and first content page. */
    public record Element(
            String role,
            String mappedRole,
            int objNum,
            DocValue.Scribble scribble,
            DocValue.Title title,
            DocValue.Scope scope,
            int page)
            implements Value {}

    /** A marked-content reference with untruncated text and immutable content kinds. */
    public record MarkedContent(
            int page, int mcid, Set<Content.ContentKind> kinds, String text, boolean orphaned)
            implements Value {
        public MarkedContent {
            kinds = Set.copyOf(kinds);
        }
    }

    /** An annotation reference, including its current and original link destinations. */
    public record ObjectReference(
            int page,
            DocValue annotation,
            DocValue.Destination destination,
            DocValue.Destination originalUri)
            implements Value {}

    /** Captures a document's structure tree, extracting content once per page. */
    public static TreeNode<Value> capture(PdfDocument doc) {
        var start = startOf(doc);
        Map<Content.PageMcid, Content.McidContent> contents = new HashMap<>();
        for (int page = 1; page <= doc.getNumberOfPages(); page++) {
            int pageNumber = page;
            Content.extractContentForPage(doc.getPage(page))
                    .forEach(
                            (mcid, content) ->
                                    contents.put(new Content.PageMcid(pageNumber, mcid), content));
        }
        return capture(start, contents);
    }

    /** Captures a document's structure tree with labels only, skipping content extraction. */
    public static TreeNode<Value> captureStructure(PdfDocument doc) {
        return capture(startOf(doc), Map.of());
    }

    /** Captures a subtree using previously extracted content, or an empty map for labels only. */
    public static TreeNode<Value> capture(
            IStructureNode source, Map<Content.PageMcid, Content.McidContent> contents) {
        if (source instanceof PdfObjRef ref) {
            DocValue annotation = DocValue.annotOf(ref);
            if (annotation == null) {
                logger.debug(
                        "No referenced object found for OBJR {}", ref.getPdfObject().toString());
            }
            return TreeNode.leaf(
                    new ObjectReference(
                            StructTree.pageOf(ref),
                            annotation,
                            DocValue.destinationOf(ref),
                            DocValue.originalUriOf(ref)));
        }
        if (source instanceof PdfMcr mcr) {
            int page = StructTree.pageOf(mcr);
            var content = contents.get(new Content.PageMcid(page, mcr.getMcid()));
            return TreeNode.leaf(
                    new MarkedContent(
                            page,
                            mcr.getMcid(),
                            content != null ? content.kinds() : Set.of(),
                            content != null ? content.text() : "",
                            mcr.getPageIndirectReference() == null));
        }

        List<TreeNode<Value>> children = new ArrayList<>();
        if (source.getKids() != null) {
            for (var child : source.getKids()) {
                if (child != null) children.add(capture(child, contents));
            }
        }
        Value value;
        if (source instanceof PdfStructElem element) {
            int firstPage =
                    children.stream()
                            .mapToInt(child -> child.value().page())
                            .filter(page -> page > 0)
                            .findFirst()
                            .orElse(0);
            value =
                    new Element(
                            element.getRole().getValue(),
                            Tag.of(element).role(),
                            Tag.of(element).objNum(),
                            DocValue.Scribble.of(element),
                            DocValue.Title.of(element),
                            DocValue.Scope.of(element),
                            firstPage);
        } else {
            value = new Root();
        }
        return new TreeNode<>(value, children);
    }

    /** Returns the Document element, or the structure root when there is none. */
    private static IStructureNode startOf(PdfDocument doc) {
        var root = doc.getStructTreeRoot();
        if (root == null) throw new IllegalStateException("PDF has no structure tree");
        var document = StructTree.findDocument(root);
        return document != null ? document : root;
    }
}
