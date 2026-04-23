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
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.StructTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeDiagramTest {

    private static final Path TAGGED_BASELINE_PDF =
            Path.of("src/test/resources/tagged_baseline.pdf");

    @TempDir Path tempDir;

    @Test
    void renderingFromStructTreeRootSkipsRootLabel() throws Exception {
        try (PdfDocument doc = openForReading()) {
            var root = doc.getStructTreeRoot();
            PdfStructElem docElem = StructTree.findDocument(root);

            String fromRoot = TreeDiagram.toIndentedTreeString(root);
            String fromDocument = TreeDiagram.toIndentedTreeString(docElem);

            assertEquals(
                    fromDocument,
                    fromRoot,
                    "Rendering from StructTreeRoot should skip its label and produce the same"
                            + " output as rendering from its Document kid");
            assertTrue(fromRoot.startsWith("Document"), "Top line should be the Document element");
        }
    }

    @Test
    void annotateFromStringWritesScribbleToMatchingElement() throws Exception {
        Path outPdf = tempDir.resolve("annotated.pdf");

        String dump = TreeDiagram.dumpToString(openForReading(), true);
        String annotated = dump.replace("H1 #56\n", "H1 #56 \"__!SET_ROLE H2\"\n");

        try (PdfDocument doc = openForModification(outPdf)) {
            TreeDiagram.AnnotateResult result =
                    TreeDiagram.annotateFromString(doc, annotated, msg -> {});

            assertEquals(1, result.updated());
            assertEquals(0, result.unmatchedLines());
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(outPdf.toString()))) {
            String redumped = TreeDiagram.dumpToString(doc, true);
            assertTrue(
                    redumped.contains("H1 #56 \"__!SET_ROLE H2\""),
                    "Re-dumped tree should contain the scribble");
        }
    }

    @Test
    void annotateFromStringClearsScribbleWhenQuoteRemoved() throws Exception {
        Path withScribble = tempDir.resolve("scribbled.pdf");
        Path cleared = tempDir.resolve("cleared.pdf");

        // First, write a scribble
        String dump = TreeDiagram.dumpToString(openForReading(), true);
        String annotated = dump.replace("P #59\n", "P #59 \"__TODO\"\n");
        try (PdfDocument doc = openForModification(withScribble)) {
            TreeDiagram.annotateFromString(doc, annotated, msg -> {});
        }

        // Now re-dump and remove the scribble
        String dumpWithScribble;
        try (PdfDocument doc = new PdfDocument(new PdfReader(withScribble.toString()))) {
            dumpWithScribble = TreeDiagram.dumpToString(doc, true);
        }
        assertTrue(dumpWithScribble.contains("P #59 \"__TODO\""));
        String withoutScribble = dumpWithScribble.replace("P #59 \"__TODO\"", "P #59");

        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(withScribble.toString()),
                        new PdfWriter(cleared.toString()))) {
            TreeDiagram.AnnotateResult result =
                    TreeDiagram.annotateFromString(doc, withoutScribble, msg -> {});

            assertEquals(1, result.cleared());
        }

        try (PdfDocument doc = new PdfDocument(new PdfReader(cleared.toString()))) {
            PdfStructElem p59 = findByObjNum(doc, 59);
            assertNotNull(p59, "P #59 should exist");
            assertNull(p59.getPdfObject().getAsString(PdfName.T), "/T should be cleared");
        }
    }

    @Test
    void annotateFromStringWarnsOnRoleMismatch() throws Exception {
        Path outPdf = tempDir.resolve("mismatch.pdf");
        String dump = TreeDiagram.dumpToString(openForReading(), true);
        String bad = dump.replace("H1 #56\n", "H3 #56 \"__bogus\"\n");

        List<String> warnings = new ArrayList<>();
        try (PdfDocument doc = openForModification(outPdf)) {
            TreeDiagram.AnnotateResult result =
                    TreeDiagram.annotateFromString(doc, bad, warnings::add);

            assertEquals(1, result.unmatchedLines());
            assertTrue(
                    warnings.stream().anyMatch(w -> w.contains("H3") && w.contains("H1")),
                    "Should warn about role mismatch");
        }
    }

    private PdfDocument openForReading() throws Exception {
        return new PdfDocument(new PdfReader(TAGGED_BASELINE_PDF.toString()));
    }

    private PdfDocument openForModification(Path out) throws Exception {
        return new PdfDocument(
                new PdfReader(TAGGED_BASELINE_PDF.toString()), new PdfWriter(out.toString()));
    }

    private static PdfStructElem findByObjNum(PdfDocument doc, int objNum) {
        return findByObjNum(doc.getStructTreeRoot(), objNum);
    }

    private static PdfStructElem findByObjNum(
            com.itextpdf.kernel.pdf.tagging.IStructureNode node, int objNum) {
        if (node instanceof PdfStructElem elem && StructTree.objNum(elem) == objNum) {
            return elem;
        }
        if (node.getKids() != null) {
            for (var kid : node.getKids()) {
                PdfStructElem found = findByObjNum(kid, objNum);
                if (found != null) return found;
            }
        }
        return null;
    }
}
