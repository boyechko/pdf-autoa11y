// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.kernel.pdf.navigation.PdfStringDestination;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutlineEditorTest {

    @TempDir Path tempDir;

    // === Parser tests (pure-string, no PDF) ===================================

    @Test
    void parsesFlatListOfRootsAtSameIndent() {
        String input = "First  @1\nSecond  @5\nThird  @10\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(0, result.errors());
        assertEquals(3, result.roots().size());
        assertEquals("First", result.roots().get(0).title());
        assertEquals(1, result.roots().get(0).page());
        assertEquals(10, result.roots().get(2).page());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void parsesNestedHierarchy() {
        String input = "Root  @1\n  Child  @2\n    Grandchild  @3\n  SecondChild  @4\n";
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, msg -> {});

        assertEquals(0, result.errors());
        assertEquals(1, result.roots().size());
        OutlineEditor.Node root = result.roots().get(0);
        assertEquals(2, root.children().size());
        assertEquals("Child", root.children().get(0).title());
        assertEquals(1, root.children().get(0).children().size());
        assertEquals("Grandchild", root.children().get(0).children().get(0).title());
        assertEquals("SecondChild", root.children().get(1).title());
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        String input = "# top-level comment\n\nFirst  @1\n  # nested comment\n  Child  @2\n\n";
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, msg -> {});

        assertEquals(0, result.errors());
        assertEquals(1, result.roots().size());
        assertEquals(1, result.roots().get(0).children().size());
    }

    @Test
    void titlesWithEmbeddedAtSurviveBecauseSigilAnchorsToEndOfLine() {
        String input = "Twitter @42 status  @5\n";
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, msg -> {});

        assertEquals(0, result.errors());
        assertEquals("Twitter @42 status", result.roots().get(0).title());
        assertEquals(5, result.roots().get(0).page());
    }

    @Test
    void parserAcceptsSingleSpaceBeforeSigil() {
        // Two-space separation is what the dumper emits for readability, but the parser
        // accepts any positive number of spaces so hand-editing (especially with vim t@)
        // doesn't require maintaining a specific space count.
        String input = "Single space @1\nTwo spaces  @2\nMany     spaces  @3\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(0, result.errors());
        assertEquals(3, result.roots().size());
        assertEquals("Single space", result.roots().get(0).title());
        assertEquals(1, result.roots().get(0).page());
        assertEquals("Many     spaces", result.roots().get(2).title());
        assertEquals(3, result.roots().get(2).page());
    }

    @Test
    void reportsMissingPageSigil() {
        String input = "No page number here\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(1, result.errors());
        assertTrue(warnings.get(0).contains("missing page sigil"));
    }

    @Test
    void reportsTabIndentation() {
        String input = "Root  @1\n\tChild  @2\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(1, result.errors());
        assertTrue(warnings.get(0).contains("tabs"));
    }

    @Test
    void reportsOddIndent() {
        String input = "Root  @1\n   Child  @2\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(1, result.errors());
        assertTrue(warnings.get(0).contains("multiple of 2"));
    }

    @Test
    void reportsSkippedIndentLevel() {
        String input = "Root  @1\n    Grandchild  @2\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(1, result.errors());
        assertTrue(warnings.get(0).contains("no parent at depth"));
    }

    @Test
    void rejectsQuestionMarkPlaceholderWithHelpfulMessage() {
        String input = "Unresolved entry  @?\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(1, result.errors());
        assertEquals(1, warnings.size());
        assertTrue(
                warnings.get(0).contains("unresolved destination"),
                "Expected message about unresolved destination, got: " + warnings.get(0));
        assertTrue(
                warnings.get(0).contains("assign a page number or delete"),
                "Expected guidance on how to fix, got: " + warnings.get(0));
    }

    @Test
    void parsingContinuesAfterErrors() {
        String input = "Good  @1\nBad line no sigil\nAlso good  @3\n";
        List<String> warnings = new ArrayList<>();
        OutlineEditor.ParseResult result = OutlineEditor.parse(input, warnings::add);

        assertEquals(1, result.errors());
        assertEquals(2, result.roots().size());
        assertEquals("Good", result.roots().get(0).title());
        assertEquals("Also good", result.roots().get(1).title());
    }

    @Test
    void emptyInputProducesNoRootsNoErrors() {
        OutlineEditor.ParseResult result = OutlineEditor.parse("", msg -> {});
        assertEquals(0, result.errors());
        assertTrue(result.roots().isEmpty());
    }

    // === Title sanitization ===================================================

    @Test
    void sanitizeStripsControlAndCollapsesWhitespace() {
        assertEquals("Hello world", OutlineEditor.sanitizeTitle("Hello\nworld"));
        assertEquals("Hello world", OutlineEditor.sanitizeTitle("Hello\tworld"));
        assertEquals("Hello world", OutlineEditor.sanitizeTitle("  Hello   world  "));
    }

    // === Dump / apply round-trip (with PDF) ===================================

    @Test
    void dumpReturnsEmptyStringForDocumentWithNoOutline() throws Exception {
        Path pdf = tempDir.resolve("no-outline.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            doc.addNewPage();
            doc.addNewPage();
        }
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf.toString()))) {
            assertEquals("", OutlineEditor.dumpToString(doc));
        }
    }

    @Test
    void dumpRendersExistingOutlineAsIndentedText() throws Exception {
        Path pdf = tempDir.resolve("with-outline.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            for (int i = 0; i < 5; i++) doc.addNewPage();
            PdfOutline root = doc.getOutlines(true);
            PdfOutline chapter = root.addOutline("Chapter 1");
            chapter.addDestination(PdfExplicitDestination.createFitH(doc.getPage(1), 792f));
            PdfOutline section = chapter.addOutline("Section 1.1");
            section.addDestination(PdfExplicitDestination.createFitH(doc.getPage(3), 792f));
            PdfOutline ch2 = root.addOutline("Chapter 2");
            ch2.addDestination(PdfExplicitDestination.createFitH(doc.getPage(5), 792f));
        }
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf.toString()))) {
            String dump = OutlineEditor.dumpToString(doc);
            assertEquals("Chapter 1  @1\n  Section 1.1  @3\nChapter 2  @5\n", dump);
        }
    }

    @Test
    void dumpEmitsQuestionMarkForUnresolvableDestination() throws Exception {
        // Outline entry points at a named destination that isn't in /Catalog/Names/Dests.
        // This matches the post-InlineDestinationsFix state: orphaned references that the
        // inliner couldn't resolve and left dangling.
        Path pdf = tempDir.resolve("orphan-dest.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            for (int i = 0; i < 3; i++) doc.addNewPage();
            PdfOutline root = doc.getOutlines(true);
            PdfOutline good = root.addOutline("Resolves fine");
            good.addDestination(PdfExplicitDestination.createFitH(doc.getPage(1), 792f));
            PdfOutline orphan = good.addOutline("Dangling reference");
            orphan.addDestination(new PdfStringDestination(new PdfString("DoesNotExist")));
            PdfOutline leafUnder = orphan.addOutline("Child of dangling");
            leafUnder.addDestination(PdfExplicitDestination.createFitH(doc.getPage(3), 792f));
        }
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf.toString()))) {
            String dump = OutlineEditor.dumpToString(doc);
            assertEquals(
                    "Resolves fine  @1\n  Dangling reference  @?\n    Child of dangling  @3\n",
                    dump,
                    "Unresolvable entry should render with @? marker without collapsing"
                            + " its children's indent depth");
        }
    }

    @Test
    void dumpResolvesNamedDestinationsAgainstDestsTree() throws Exception {
        // Reproduces the Web Capture pattern that previously caused entries to be silently
        // dropped: outline /Dest is a PdfString that names an entry in /Catalog/Names/Dests
        // rather than an inline [page /XYZ ...] array.
        Path pdf = tempDir.resolve("named-dest.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            for (int i = 0; i < 4; i++) doc.addNewPage();
            PdfArray destArray = new PdfArray();
            destArray.add(doc.getPage(3).getPdfObject());
            destArray.add(PdfName.XYZ);
            destArray.add(new com.itextpdf.kernel.pdf.PdfNumber(0));
            destArray.add(new com.itextpdf.kernel.pdf.PdfNumber(792));
            destArray.add(new com.itextpdf.kernel.pdf.PdfNumber(0));
            doc.addNamedDestination("MyAnchor", destArray);

            PdfOutline root = doc.getOutlines(true);
            PdfOutline entry = root.addOutline("Via named destination");
            entry.addDestination(new PdfStringDestination(new PdfString("MyAnchor")));
        }
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf.toString()))) {
            String dump = OutlineEditor.dumpToString(doc);
            assertEquals(
                    "Via named destination  @3\n",
                    dump,
                    "Named destinations should be resolved against /Catalog/Names/Dests");
        }
    }

    @Test
    void applyWipesAndReplacesExistingOutline() throws Exception {
        Path pdf = tempDir.resolve("apply.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            for (int i = 0; i < 10; i++) doc.addNewPage();
            PdfOutline root = doc.getOutlines(true);
            PdfOutline junk = root.addOutline("Junk entry to be replaced");
            junk.addDestination(PdfExplicitDestination.createFitH(doc.getPage(1), 792f));
        }

        Path out = tempDir.resolve("applied.pdf");
        String newOutline =
                "Replacement Root  @1\n  Child A  @3\n  Child B  @7\nSecond Root  @10\n";
        List<String> warnings = new ArrayList<>();
        try (PdfDocument doc =
                new PdfDocument(new PdfReader(pdf.toString()), new PdfWriter(out.toString()))) {
            OutlineEditor.ApplyResult result =
                    OutlineEditor.applyFromString(doc, newOutline, warnings::add);
            assertEquals(1, result.previousEntries());
            assertEquals(4, result.newEntries());
            assertEquals(0, result.parseErrors());
        }
        assertTrue(warnings.isEmpty());

        try (PdfDocument doc = new PdfDocument(new PdfReader(out.toString()))) {
            assertEquals(newOutline, OutlineEditor.dumpToString(doc));
        }
    }

    @Test
    void applySkipsEntriesWithOutOfRangePage() throws Exception {
        Path pdf = tempDir.resolve("range.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            doc.addNewPage();
            doc.addNewPage();
            doc.getOutlines(true);
        }

        Path out = tempDir.resolve("range-out.pdf");
        String bad = "OK  @1\nOut of range  @999\nAlso OK  @2\n";
        List<String> warnings = new ArrayList<>();
        try (PdfDocument doc =
                new PdfDocument(new PdfReader(pdf.toString()), new PdfWriter(out.toString()))) {
            OutlineEditor.ApplyResult result =
                    OutlineEditor.applyFromString(doc, bad, warnings::add);
            assertEquals(2, result.newEntries());
        }
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("out of range"));
    }

    @Test
    void applyRefusesReadOnlyDocument() throws Exception {
        Path pdf = tempDir.resolve("readonly.pdf");
        try (PdfDocument doc = new PdfDocument(new PdfWriter(pdf.toString()))) {
            doc.addNewPage();
        }
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf.toString()))) {
            assertThrows(
                    IllegalStateException.class,
                    () -> OutlineEditor.applyFromString(doc, "X  @1\n", msg -> {}));
        }
    }
}
