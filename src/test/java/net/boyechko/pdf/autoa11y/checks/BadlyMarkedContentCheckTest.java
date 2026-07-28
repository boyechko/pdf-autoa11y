// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.nio.file.Path;
import java.util.List;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import org.junit.jupiter.api.Test;

class BadlyMarkedContentCheckTest extends PdfTestBase {

    /**
     * A catalog cover page with two page-1 blocks that lump differently-sized lines: an H2 (MCID
     * 15) and a P (MCID 16). The P also wraps a same-size line, which must not be miscounted as a
     * mixed font.
     */
    private static final Path CATALOG_067 = Path.of("src/test/resources/catalog_067.pdf");

    @Test
    void flagsBlocksThatLumpDifferentlyFontedLines() throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(CATALOG_067.toString()))) {
            List<Issue> flagged = mixedFontIssues(doc);

            assertEquals(2, flagged.size(), "the H2 and the P both lump differently-sized lines");
            assertTrue(
                    flagged.stream().allMatch(issue -> issue.fix() != null),
                    "each flagged block carries a split fix");
            assertTrue(
                    flagged.stream()
                            .anyMatch(issue -> issue.message().contains("Graduate Programs")),
                    "the H2 block is flagged");
            assertTrue(
                    flagged.stream()
                            .anyMatch(issue -> issue.message().contains("Program of Study")),
                    "the P block is flagged");
        }
    }

    @Test
    void splittingTheFlaggedBlocksResolvesTheCheck() throws Exception {
        try (PdfDocument doc =
                new PdfDocument(
                        new PdfReader(CATALOG_067.toString()), new PdfWriter(testOutputStream()))) {
            DocContext ctx = new DocContext(doc);
            List<Issue> flagged = mixedFontIssues(doc);
            assertEquals(2, flagged.size(), "precondition: two flagged blocks");

            for (Issue issue : flagged) {
                issue.fix().apply(ctx);
            }

            assertEquals(
                    0,
                    mixedFontIssues(doc).size(),
                    "splitting each block leaves only single-font blocks");
        }
    }

    private static List<Issue> mixedFontIssues(PdfDocument doc) {
        return new BadlyMarkedContentCheck()
                .findIssues(new DocContext(doc)).stream()
                        .filter(issue -> issue.type() == IssueType.MIXED_FONT_MARKED_CONTENT)
                        .toList();
    }
}
