// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class MergeSplitLinksFixTest extends PdfTestBase {

    private static final String SHARED_URI = "https://uw.edu/catalog/Education-875.html";

    /** Builds a P holding one Link per string, all pointing at the same destination. */
    private static Paragraph splitLink(String... pieces) {
        Paragraph p = new Paragraph();
        for (String piece : pieces) {
            p.add(new Link(piece, PdfAction.createURI(SHARED_URI)));
        }
        return p;
    }

    private static PdfStructElem paragraphOf(PdfDocument pdfDoc) {
        PdfStructElem document = StructTree.findDocument(pdfDoc.getStructTreeRoot());
        return StructTree.findFirstChild(document, PdfName.P);
    }

    @Test
    void mergesSplitLinksIntoOneElement() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(splitLink("Program of Study: Major:", "Developmental and Youth Studies"));

            PdfStructElem p = paragraphOf(pdfDoc);
            List<PdfStructElem> links = StructTree.childrenOf(p, PdfStructElem.class);
            assertEquals(2, links.size(), "Expected two Link tags before the fix");

            new MergeSplitLinksFix(links).apply(new DocContext(pdfDoc));

            assertEquals(
                    "P[Link[]]", StructTree.toRoleTree(p).toString(), "Links should be merged");
            PdfStructElem merged = StructTree.childrenOf(p, PdfStructElem.class).get(0);
            assertEquals(
                    2, StructTree.childrenOf(merged, PdfObjRef.class).size(), "Kept both OBJRs");
            assertEquals(2, StructTree.childrenOf(merged, PdfMcr.class).size(), "Kept both MCRs");
        }
    }

    @Test
    void groupsObjRefsAheadOfContent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(splitLink("https://www", ".pdfa.org"));

            PdfStructElem p = paragraphOf(pdfDoc);
            new MergeSplitLinksFix(StructTree.childrenOf(p, PdfStructElem.class))
                    .apply(new DocContext(pdfDoc));

            PdfStructElem merged = StructTree.childrenOf(p, PdfStructElem.class).get(0);
            List<IStructureNode> kids = merged.getKids();
            int lastObjRef = -1;
            int firstContent = kids.size();
            for (int i = 0; i < kids.size(); i++) {
                if (kids.get(i) instanceof PdfObjRef) {
                    lastObjRef = i;
                } else if (firstContent == kids.size()) {
                    firstContent = i;
                }
            }
            assertTrue(lastObjRef < firstContent, "All OBJRs should precede the content MCRs");
        }
    }

    @Test
    void leavesLinksAloneWhenNoLongerAdjacent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(splitLink("first half", "second half"));

            PdfStructElem p = paragraphOf(pdfDoc);
            List<PdfStructElem> links = StructTree.childrenOf(p, PdfStructElem.class);
            // An earlier fix moved the second Link out from between them
            p.removeKid(links.get(1));

            new MergeSplitLinksFix(links).apply(new DocContext(pdfDoc));

            assertEquals(
                    "P[Link[]]", StructTree.toRoleTree(p).toString(), "Should not have merged");
            assertEquals(
                    1,
                    StructTree.childrenOf(
                                    StructTree.childrenOf(p, PdfStructElem.class).get(0),
                                    PdfObjRef.class)
                            .size(),
                    "The surviving Link should keep only its own OBJR");
        }
    }

    @Test
    void stampsMergedLinkWithToolScribble() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(splitLink("Back to ", "top"));

            PdfStructElem p = paragraphOf(pdfDoc);
            new MergeSplitLinksFix(StructTree.childrenOf(p, PdfStructElem.class))
                    .apply(new DocContext(pdfDoc));

            PdfStructElem merged = StructTree.childrenOf(p, PdfStructElem.class).get(0);
            DocValue.Scribble scribble = StructTree.getScribble(merged);
            assertNotNull(scribble, "Merged Link should carry a scribble");
            assertTrue(scribble.toolAuthored(), "Scribble should read as tool-authored");
            assertEquals(List.of("LINKS MERGED"), scribble.segments());
        }
    }

    @Test
    void keepsAnExistingScribbleWhenStamping() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(splitLink("Back to ", "top"));

            PdfStructElem p = paragraphOf(pdfDoc);
            List<PdfStructElem> links = StructTree.childrenOf(p, PdfStructElem.class);
            StructTree.setScribble(links.get(0), "check me");

            new MergeSplitLinksFix(links).apply(new DocContext(pdfDoc));

            PdfStructElem merged = StructTree.childrenOf(p, PdfStructElem.class).get(0);
            assertEquals(
                    List.of("check me", "LINKS MERGED"),
                    StructTree.getScribble(merged).segments(),
                    "Should append to the user's scribble rather than replace it");
        }
    }

    @Test
    void isIdempotent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            layoutDoc.add(splitLink("Back to ", "top"));

            PdfStructElem p = paragraphOf(pdfDoc);
            MergeSplitLinksFix fix =
                    new MergeSplitLinksFix(StructTree.childrenOf(p, PdfStructElem.class));
            fix.apply(new DocContext(pdfDoc));
            fix.apply(new DocContext(pdfDoc));

            assertEquals("P[Link[]]", StructTree.toRoleTree(p).toString(), "Should stay merged");
            PdfStructElem merged = StructTree.childrenOf(p, PdfStructElem.class).get(0);
            assertEquals(
                    2, StructTree.childrenOf(merged, PdfObjRef.class).size(), "Kept both OBJRs");
            assertEquals(
                    List.of("LINKS MERGED"),
                    StructTree.getScribble(merged).segments(),
                    "Should not stamp the scribble twice");
        }
    }
}
