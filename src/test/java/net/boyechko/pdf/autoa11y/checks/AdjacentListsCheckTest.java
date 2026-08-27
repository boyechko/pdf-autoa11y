// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.RoleMap;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class AdjacentListsCheckTest extends PdfTestBase {

    @Test
    void mergesAdjacentListsWithoutBulletEvidence() throws Exception {
        try (PdfDocument pdfDoc = newDocument()) {
            PdfStructElem document = documentOf(pdfDoc);
            listWithItems(pdfDoc, document, PdfName.L, 1);
            listWithItems(pdfDoc, document, PdfName.L, 2);

            AdjacentListsCheck check = check(pdfDoc);

            assertEquals(1, check.getIssues().size());
            assertEquals(IssueType.ADJACENT_LISTS, check.getIssues().get(0).type());

            check.getIssues().applyFixes(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[]], LI[LBody[]], LI[LBody[]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void mergesEveryListInAnAdjacentRunIntoTheFirst() throws Exception {
        try (PdfDocument pdfDoc = newDocument()) {
            PdfStructElem document = documentOf(pdfDoc);
            listWithItems(pdfDoc, document, PdfName.L, 1);
            listWithItems(pdfDoc, document, PdfName.L, 1);
            listWithItems(pdfDoc, document, PdfName.L, 1);

            AdjacentListsCheck check = check(pdfDoc);

            assertEquals(2, check.getIssues().size());

            check.getIssues().applyFixes(new DocContext(pdfDoc));

            assertEquals(
                    "Document[L[LI[LBody[]], LI[LBody[]], LI[LBody[]]]]",
                    StructTree.toRoleTree(document).toString());
        }
    }

    @Test
    void recognizesMappedListRoles() throws Exception {
        try (PdfDocument pdfDoc = newDocument()) {
            PdfName customList = RoleMap.toPdfName("CustomList");
            PdfDictionary roleMap = new PdfDictionary();
            roleMap.put(customList, PdfName.L);
            pdfDoc.getStructTreeRoot().getPdfObject().put(PdfName.RoleMap, roleMap);

            PdfStructElem document = documentOf(pdfDoc);
            listWithItems(pdfDoc, document, customList, 1);
            listWithItems(pdfDoc, document, customList, 1);

            AdjacentListsCheck check = check(pdfDoc);

            assertEquals(1, check.getIssues().size());
        }
    }

    @Test
    void ignoresListsSeparatedByAnotherElement() throws Exception {
        try (PdfDocument pdfDoc = newDocument()) {
            PdfStructElem document = documentOf(pdfDoc);
            listWithItems(pdfDoc, document, PdfName.L, 1);
            document.addKid(new PdfStructElem(pdfDoc, PdfName.P));
            listWithItems(pdfDoc, document, PdfName.L, 1);

            AdjacentListsCheck check = check(pdfDoc);

            assertTrue(check.getIssues().isEmpty());
        }
    }

    @Test
    void ignoresListsSeparatedByRawMarkedContent() throws Exception {
        try (PdfDocument pdfDoc = newDocument()) {
            PdfStructElem document = documentOf(pdfDoc);
            listWithItems(pdfDoc, document, PdfName.L, 1);
            document.addKid(new PdfMcrNumber(new PdfNumber(0), document));
            listWithItems(pdfDoc, document, PdfName.L, 1);

            AdjacentListsCheck check = check(pdfDoc);

            assertTrue(check.getIssues().isEmpty());
        }
    }

    private PdfDocument newDocument() {
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()));
        pdfDoc.setTagged();
        pdfDoc.addNewPage();
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        root.addKid(new PdfStructElem(pdfDoc, PdfName.Document));
        return pdfDoc;
    }

    private static PdfStructElem documentOf(PdfDocument pdfDoc) {
        return StructTree.findDocument(pdfDoc.getStructTreeRoot());
    }

    private static PdfStructElem listWithItems(
            PdfDocument pdfDoc, PdfStructElem parent, PdfName role, int itemCount) {
        PdfStructElem list = new PdfStructElem(pdfDoc, role);
        parent.addKid(list);
        for (int i = 0; i < itemCount; i++) {
            PdfStructElem item = new PdfStructElem(pdfDoc, PdfName.LI);
            list.addKid(item);
            item.addKid(new PdfStructElem(pdfDoc, PdfName.LBody));
        }
        return list;
    }

    private static AdjacentListsCheck check(PdfDocument pdfDoc) {
        AdjacentListsCheck check = new AdjacentListsCheck();
        check.findIssues(new DocContext(pdfDoc));
        return check;
    }
}
