/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.ConvertToArtifact;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import org.junit.jupiter.api.Test;

class MistaggedArtifactRuleTest {

    // Patterns copied from MistaggedArtifactRule for testing
    private static final Pattern FOOTER_URL_TIMESTAMP =
            Pattern.compile(
                    "https?://[^\\s]+.*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TIMESTAMP_ONLY =
            Pattern.compile(
                    "^\\s*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_NUMBER =
            Pattern.compile("^\\s*(Page\\s+)?\\d+\\s*(of\\s+\\d+)?\\s*$", Pattern.CASE_INSENSITIVE);

    @Test
    void patternMatchesUrlWithTimestamp() {
        String footerText = "https://www.uwb.edu/catalog/ [11/15/2024 11:37:19 AM]";
        assertTrue(FOOTER_URL_TIMESTAMP.matcher(footerText).find());
    }

    @Test
    void patternMatchesHttpsUrl() {
        String footerText = "https://example.com/path/to/page [1/5/2024 9:00:00 PM]";
        assertTrue(FOOTER_URL_TIMESTAMP.matcher(footerText).find());
    }

    @Test
    void patternMatchesTimestampOnly() {
        String timestampText = "[11/15/2024 11:37:19 AM]";
        assertTrue(TIMESTAMP_ONLY.matcher(timestampText).matches());
    }

    @Test
    void patternMatchesTimestampWithWhitespace() {
        String timestampText = "  [1/5/2024 9:00:00 PM]  ";
        assertTrue(TIMESTAMP_ONLY.matcher(timestampText).matches());
    }

    @Test
    void patternMatchesPageNumber() {
        assertTrue(PAGE_NUMBER.matcher("1").matches());
        assertTrue(PAGE_NUMBER.matcher("42").matches());
        assertTrue(PAGE_NUMBER.matcher("Page 1").matches());
        assertTrue(PAGE_NUMBER.matcher("Page 42 of 100").matches());
        assertTrue(PAGE_NUMBER.matcher("  1 of 10  ").matches());
    }

    @Test
    void patternDoesNotMatchNormalText() {
        String normalText = "This is a normal paragraph about accessibility.";
        assertFalse(FOOTER_URL_TIMESTAMP.matcher(normalText).find());
        assertFalse(TIMESTAMP_ONLY.matcher(normalText).matches());
        assertFalse(PAGE_NUMBER.matcher(normalText).matches());
    }

    @Test
    void noIssuesForEmptyTree() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            // Empty tree - no children added

            DocumentContext ctx = new DocumentContext(pdfDoc);
            MistaggedArtifactRule rule = new MistaggedArtifactRule();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(issues.isEmpty(), "Empty tree should have no issues");
        }
    }

    @Test
    void noIssuesForStructureOnlyTree() throws Exception {
        // Tree with structure but no MCR content won't match patterns
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            MistaggedArtifactRule rule = new MistaggedArtifactRule();
            IssueList issues = rule.findIssues(ctx);

            assertTrue(issues.isEmpty(), "Structure without MCR content should have no issues");
        }
    }

    @Test
    void fixRemovesElementFromParent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            ConvertToArtifact fix = new ConvertToArtifact(p);

            // Before fix
            List<IStructureNode> kidsBefore = document.getKids();
            assertEquals(1, kidsBefore.size());

            // Apply fix
            fix.apply(ctx);

            // After fix
            List<IStructureNode> kidsAfter = document.getKids();
            assertTrue(kidsAfter == null || kidsAfter.isEmpty(), "Element should be removed");
        }
    }

    @Test
    void fixIsIdempotent() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem document = new PdfStructElem(pdfDoc, PdfName.Document);
            root.addKid(document);
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            document.addKid(p);

            DocumentContext ctx = new DocumentContext(pdfDoc);
            ConvertToArtifact fix = new ConvertToArtifact(p);

            // Apply fix twice - should not throw
            fix.apply(ctx);
            assertDoesNotThrow(() -> fix.apply(ctx));
        }
    }

    @Test
    void fixDescribeIncludesRole() throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            pdfDoc.setTagged();
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            PdfStructElem p = new PdfStructElem(pdfDoc, new PdfName("P"));
            root.addKid(p);

            ConvertToArtifact fix = new ConvertToArtifact(p);
            String description = fix.describe();

            assertTrue(description.contains("P"), "Description should include role");
            assertTrue(description.contains("artifact"), "Description should mention artifact");
        }
    }
}
