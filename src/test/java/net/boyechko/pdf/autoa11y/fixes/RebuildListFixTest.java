// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

/**
 * The in-memory tests give each content element its own role, so the role tree shows where each one
 * lands and not just the shape of the list.
 */
class RebuildListFixTest extends PdfTestBase {

    /**
     * A catalog page whose Div #25 sets five bullets with two sublists: P #26 lumps item 1 with its
     * two sub-bullets, P #30, #33 and #36 are items 2-4 in L #27, P #40, #43 and #46 are item 4's
     * sub-bullets in L #37 beside it, and P #50 is item 5 in L #47.
     */
    private static final Path SPLIT_LIST_PDF = Path.of("src/test/resources/catalog_123.pdf");

    @Test
    void splitsALumpedElementIntoOneItemPerBullet() throws Exception {
        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(SPLIT_LIST_PDF.toString()),
                        new PdfWriter(testOutputStream()))) {
            PdfStructElem lumped = elementByObjNum(doc, 26);

            new RebuildListFix(
                            List.of(
                                    new RebuildListFix.Piece(lumped, 1, List.of(0, 1, 1), "1,1,1"),
                                    piece(elementByObjNum(doc, 30), 0)))
                    .apply(new DocContext(doc));

            assertEquals(
                    "LI[LBody[P[],L[LI[LBody[P[]]],LI[LBody[P[]]]]]]",
                    StructTree.toRoleTreeString(itemOf(lumped)),
                    "item 1 keeps its own line and holds its two sub-bullets as a sublist");
        }
    }

    @Test
    void nestsASublistInsideTheItemItsBulletsSetItUnder() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            // The sublist sits beside the list holding its parent item, not inside that item
            PdfStructElem list = addKid(pdfDoc, document, PdfName.L);
            PdfStructElem first = addItem(pdfDoc, list, PdfName.P);
            PdfStructElem parent = addItem(pdfDoc, list, PdfName.Span);
            PdfStructElem sublist = addKid(pdfDoc, document, PdfName.L);
            PdfStructElem child1 = addItem(pdfDoc, sublist, PdfName.Code);
            PdfStructElem child2 = addItem(pdfDoc, sublist, PdfName.Quote);

            new RebuildListFix(
                            List.of(
                                    piece(first, 0),
                                    piece(parent, 0),
                                    piece(child1, 1),
                                    piece(child2, 1)))
                    .apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[P[]]],LI[LBody[Span[],L[LI[LBody[Code[]]],LI[LBody[Quote[]]]]]]]]",
                    StructTree.toRoleTreeString(document),
                    "the sublist opens in the parent item's LBody");
        }
    }

    @Test
    void gathersItemsTaggedInSeparateListsIntoOne() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            // A loose item, two items in one list, and a last item in a list of its own
            PdfStructElem loose = addKid(pdfDoc, document, PdfName.P);
            PdfStructElem list = addKid(pdfDoc, document, PdfName.L);
            PdfStructElem second = addItem(pdfDoc, list, PdfName.Span);
            PdfStructElem third = addItem(pdfDoc, list, PdfName.Code);
            PdfStructElem fourth =
                    addItem(pdfDoc, addKid(pdfDoc, document, PdfName.L), PdfName.Quote);

            new RebuildListFix(
                            List.of(
                                    piece(loose, 0),
                                    piece(second, 0),
                                    piece(third, 0),
                                    piece(fourth, 0)))
                    .apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[P[]]],LI[LBody[Span[]]],LI[LBody[Code[]]],LI[LBody[Quote[]]]]]",
                    StructTree.toRoleTreeString(document));
        }
    }

    @Test
    void continuationJoinsTheItemBeforeIt() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            // The opener's item runs on into a P tagged outside any list
            PdfStructElem opener = addItem(pdfDoc, addKid(pdfDoc, document, PdfName.L), PdfName.P);
            PdfStructElem continuation = addKid(pdfDoc, document, PdfName.Span);
            PdfStructElem next = addItem(pdfDoc, addKid(pdfDoc, document, PdfName.L), PdfName.Code);

            new RebuildListFix(
                            List.of(piece(opener, 0), continuation(continuation), piece(next, 0)))
                    .apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[P[],Span[]]],LI[LBody[Code[]]]]]",
                    StructTree.toRoleTreeString(document),
                    "the continuation finishes the item its opener starts, in the same LBody");
        }
    }

    @Test
    void contentFlattenedOutOfAWrapperKeepsItsReadingOrder() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.setTagged();
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            // The first list holds two headings as items ahead of the bullet that opens it
            addKid(pdfDoc, document, PdfName.H1);
            PdfStructElem wrapper = addKid(pdfDoc, document, PdfName.L);
            addItem(pdfDoc, wrapper, PdfName.H2);
            addItem(pdfDoc, wrapper, PdfName.H3);
            PdfStructElem opener = addItem(pdfDoc, wrapper, PdfName.P);
            PdfStructElem next = addItem(pdfDoc, addKid(pdfDoc, document, PdfName.L), PdfName.P);
            addKid(pdfDoc, document, PdfName.H4);

            new RebuildListFix(List.of(piece(opener, 0), piece(next, 0)))
                    .apply(new DocContext(pdfDoc));

            assertEquals(
                    "Document[H1[],H2[],H3[],L[LI[LBody[P[]]],LI[LBody[P[]]]],H4[]]",
                    StructTree.toRoleTreeString(document),
                    "the headings the wrapper held stand in order, ahead of the rebuilt list");
        }
    }

    // == Helpers =========================================================

    /** A page-1 piece opening one item at the given depth. */
    private static RebuildListFix.Piece piece(PdfStructElem element, int depth) {
        return new RebuildListFix.Piece(element, 1, List.of(depth), null);
    }

    /** A page-1 piece continuing the item before it. */
    private static RebuildListFix.Piece continuation(PdfStructElem element) {
        return new RebuildListFix.Piece(element, 1, List.of(), null);
    }

    /** Appends a new element of the given role to the parent and returns it. */
    private static PdfStructElem addKid(PdfDocument pdfDoc, PdfStructElem parent, PdfName role) {
        PdfStructElem kid = new PdfStructElem(pdfDoc, role);
        parent.addKid(kid);
        return kid;
    }

    /** Appends an LI &gt; LBody item to the list and returns its new content element. */
    private static PdfStructElem addItem(PdfDocument pdfDoc, PdfStructElem list, PdfName role) {
        PdfStructElem lBody = addKid(pdfDoc, addKid(pdfDoc, list, PdfName.LI), PdfName.LBody);
        return addKid(pdfDoc, lBody, role);
    }

    /** Returns the LI holding an item's content element. */
    private static PdfStructElem itemOf(PdfStructElem content) {
        return StructTree.parentOf(StructTree.parentOf(content));
    }

    private static PdfStructElem elementByObjNum(PdfDocument doc, int objNum) {
        return StructTree.findByObjNumber(doc.getStructTreeRoot(), objNum);
    }
}
