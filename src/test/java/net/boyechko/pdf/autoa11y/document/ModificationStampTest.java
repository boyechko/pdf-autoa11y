// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Test suite for ModificationStamp. */
public class ModificationStampTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 8, 21, 12, 1);
    private static final String STAMP = "__:STAMP 2026-08-21 Fri 12:01";
    private static final String NOTE = "modified using PDF-AutoA11y";

    @Test
    void documentGetsToolAuthoredTimestamp() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            PdfStructElem document = addRootKid(doc, PdfName.Document);

            ModificationStamp.apply(doc, WHEN);

            assertEquals(STAMP, StructTree.getScribble(document).rawValue());
        }
    }

    @Test
    void restampingReplacesEarlierTimestamp() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            PdfStructElem document = addRootKid(doc, PdfName.Document);

            ModificationStamp.apply(doc, WHEN.minusDays(3));
            ModificationStamp.apply(doc, WHEN);

            assertEquals(STAMP, StructTree.getScribble(document).rawValue());
        }
    }

    @Test
    void existingSegmentsAndAuthorshipArePreserved() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            PdfStructElem document = addRootKid(doc, PdfName.Document);
            StructTree.setScribble(document, "OK");

            ModificationStamp.apply(doc, WHEN);

            assertEquals(
                    "__OK" + StructTree.SCRIBBLE_SEPARATOR + "STAMP 2026-08-21 Fri 12:01",
                    StructTree.getScribble(document).rawValue());
        }
    }

    @Test
    void segmentMerelyBeginningWithTheTagIsNotTreatedAsAnEarlierStamp() {
        try (PdfDocument doc = newTaggedDocument()) {
            PdfStructElem document = addRootKid(doc, PdfName.Document);
            StructTree.setScribble(document, "STAMPING needed on figure 4");

            ModificationStamp.apply(doc, WHEN);

            assertEquals(
                    "__STAMPING needed on figure 4"
                            + StructTree.SCRIBBLE_SEPARATOR
                            + "STAMP 2026-08-21 Fri 12:01",
                    StructTree.getScribble(document).rawValue());
        }
    }

    @Test
    void firstRootKidIsStampedWhenDocumentElementIsAbsent() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            PdfStructElem part = addRootKid(doc, PdfName.Part);

            ModificationStamp.apply(doc, WHEN);

            assertEquals(STAMP, StructTree.getScribble(part).rawValue());
        }
    }

    @Test
    void emptyStructureTreeIsNotStamped() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            assertDoesNotThrow(() -> ModificationStamp.apply(doc, WHEN));
        }
    }

    // The note carries a trailing version only when the build resolved one from a git tag, so
    // these assert around the note rather than on an exact creator string.

    @Test
    void creatorNoteIsAppendedAfterAuthoringApplication() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            doc.getDocumentInfo().setCreator("Acrobat PDFMaker 21");

            ModificationStamp.apply(doc, WHEN);

            assertTrue(
                    doc.getDocumentInfo().getCreator().startsWith("Acrobat PDFMaker 21; " + NOTE));
        }
    }

    @Test
    void restampingDoesNotAccumulateCreatorNotes() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            doc.getDocumentInfo().setCreator("Acrobat PDFMaker 21");

            ModificationStamp.apply(doc, WHEN);
            String once = doc.getDocumentInfo().getCreator();
            ModificationStamp.apply(doc, WHEN);

            assertEquals(once, doc.getDocumentInfo().getCreator());
            assertEquals(1, countOccurrences(once, NOTE));
        }
    }

    @Test
    void creatorNoteStandsAloneWhenDocumentHasNoCreator() throws Exception {
        try (PdfDocument doc = newTaggedDocument()) {
            ModificationStamp.apply(doc, WHEN);

            assertTrue(doc.getDocumentInfo().getCreator().startsWith(NOTE));
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    private static PdfDocument newTaggedDocument() {
        PdfDocument doc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()));
        doc.setTagged();
        doc.addNewPage();
        return doc;
    }

    private static PdfStructElem addRootKid(PdfDocument doc, PdfName role) {
        return doc.getStructTreeRoot().addKid(new PdfStructElem(doc, role));
    }
}
