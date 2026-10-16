// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import org.junit.jupiter.api.Test;

class ArtifactInstructionTest extends PdfTestBase {

    @Test
    void artifactInstructionDelegatesToMistaggedArtifactFix() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(testOutputStream()))) {
            pdfDoc.addNewPage();
            PdfStructTreeRoot root = new PdfStructTreeRoot(pdfDoc);
            PdfStructElem p = new PdfStructElem(pdfDoc, PdfName.P);
            root.addKid(p);

            DocContext ctx = new DocContext(pdfDoc);
            assertDoesNotThrow(() -> new ScribbledInstructionFix(p, "!ARTIFACT").apply(ctx));
        }
    }
}
