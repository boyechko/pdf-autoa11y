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
package net.boyechko.pdf.autoa11y.fixes;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;

class OrphanedContentFixTest extends PdfTestBase {

    @Test
    void removesOrphanedMcrFromParent() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfMcrNumber orphan = new PdfMcrNumber(new PdfNumber(0), p);
            p.addKid(orphan);
            PdfMcrNumber sibling = new PdfMcrNumber(new PdfNumber(1), p);
            p.addKid(sibling);

            new OrphanedContentFix(p, orphan).apply(new DocContext(doc));

            List<IStructureNode> kids = p.getKids();
            assertEquals(1, kids.size(), "only the orphan should be removed");
        }
    }

    @Test
    void prunesParentThatBecomesEmpty() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem siblingP = new PdfStructElem(doc, PdfName.P);
            document.addKid(siblingP);
            PdfStructElem orphanP = new PdfStructElem(doc, PdfName.P);
            document.addKid(orphanP);
            PdfMcrNumber orphan = new PdfMcrNumber(new PdfNumber(0), orphanP);
            orphanP.addKid(orphan);

            new OrphanedContentFix(orphanP, orphan).apply(new DocContext(doc));

            List<IStructureNode> documentKids = document.getKids();
            assertEquals(1, documentKids.size(), "empty orphan P should be pruned from Document");
            assertSame(
                    siblingP.getPdfObject(), ((PdfStructElem) documentKids.get(0)).getPdfObject());
        }
    }

    @Test
    void cascadesPruneUpToButNotIncludingDocument() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem section = new PdfStructElem(doc, PdfName.Sect);
            document.addKid(section);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            section.addKid(p);
            PdfMcrNumber orphan = new PdfMcrNumber(new PdfNumber(0), p);
            p.addKid(orphan);

            new OrphanedContentFix(p, orphan).apply(new DocContext(doc));

            List<IStructureNode> documentKids = document.getKids();
            assertTrue(
                    documentKids == null || documentKids.isEmpty(),
                    "Section -> P -> orphan: empty chain should be pruned");
            assertNotNull(
                    StructTree.findDocument(root), "Document itself remains as child of root");
        }
    }

    @Test
    void leavesParentIntactWhenStillHasKids() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfStructElem h1 = new PdfStructElem(doc, PdfName.H1);
            document.addKid(h1);
            PdfMcrNumber orphan = new PdfMcrNumber(new PdfNumber(0), p);
            p.addKid(orphan);

            new OrphanedContentFix(p, orphan).apply(new DocContext(doc));

            // p is now empty and should be pruned, but document keeps h1
            List<IStructureNode> documentKids = document.getKids();
            assertEquals(1, documentKids.size());
            assertSame(h1.getPdfObject(), ((PdfStructElem) documentKids.get(0)).getPdfObject());
        }
    }

    @Test
    void idempotentWhenOrphanAlreadyRemoved() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            doc.setTagged();
            doc.addNewPage();

            PdfStructTreeRoot root = doc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(doc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(doc, PdfName.P);
            document.addKid(p);
            PdfMcrNumber orphan = new PdfMcrNumber(new PdfNumber(0), p);
            p.addKid(orphan);
            PdfMcrNumber keeper = new PdfMcrNumber(new PdfNumber(1), p);
            p.addKid(keeper);

            OrphanedContentFix fix = new OrphanedContentFix(p, orphan);
            fix.apply(new DocContext(doc));
            assertDoesNotThrow(() -> fix.apply(new DocContext(doc)));

            assertEquals(1, p.getKids().size(), "keeper still present after second apply");
        }
    }
}
