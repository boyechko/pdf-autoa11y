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
package net.boyechko.pdf.autoa11y;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.core.NoOpProcessingListener;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.core.ProcessingService;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Goal-driven integration tests. Each test runs the full remediation pipeline on a real PDF and
 * compares the output structure tree against a hand-crafted goal snapshot.
 *
 * <p>Workflow for adding a new test case:
 *
 * <ol>
 *   <li>Run {@code ./pdf-autoa11y --dump-tree inputs/foo.pdf} to see the "before" tree
 *   <li>Manually remediate the PDF (e.g., in Acrobat)
 *   <li>Run {@code ./pdf-autoa11y --dump-tree foo_fixed.pdf >
 *       src/test/resources/snapshots/foo.goal.txt}
 *   <li>Add "foo.pdf" to the {@code @ValueSource} below
 *   <li>Run tests — they fail (red) until automated fixes match the goal (green)
 * </ol>
 */
public class GoalDrivenIntegrationTest extends PdfTestBase {

    private static final Path GOALS_DIR = Path.of("src/test/resources/goals");

    @ParameterizedTest(name = "remediate {0}")
    @ValueSource(
            strings = {
                "catalog001_a11y.pdf",
                "catalog006_a11y.pdf",
                "catalog018_a11y.pdf",
            })
    @Tag("GoalDriven")
    void remediateAndCompareToGoal(String pdfName) throws Exception {
        Path inputPath = GOALS_DIR.resolve(pdfName);
        assumeTrue(Files.exists(inputPath), "Input PDF not found: " + inputPath);

        String baseName = pdfName.replace(".pdf", "");
        Path goalFile = GOALS_DIR.resolve(baseName + ".goal.txt");
        assumeTrue(Files.exists(goalFile), "Goal snapshot not found: " + goalFile);

        ProcessingService service =
                new ProcessingService.ProcessingServiceBuilder()
                        .withPdfCustodian(new PdfCustodian(inputPath))
                        .withListener(new NoOpProcessingListener())
                        .build();

        ProcessingResult result = service.remediate();
        saveRemediatedPdf(result);

        // Remove obj numbers from tree to make it easier to compare
        String actualTree = readStructureTree(result.tempOutputFile()).replaceAll(" #\\d+", "");
        String expectedTree = Files.readString(goalFile).strip().replaceAll(" #\\d+", "");

        if (!expectedTree.equals(actualTree)) {
            fail(
                    "Structure tree of remediated "
                            + pdfName
                            + " does not match goal.\n"
                            + "To update the goal, manually remediate the PDF and run:\n"
                            + "  ./pdf-autoa11y --dump-tree <fixed.pdf> > "
                            + goalFile
                            + "\n\n"
                            + unifiedDiff(expectedTree, actualTree));
        }
    }

    private String readStructureTree(Path pdfPath) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PdfStructElem docElem = StructureTree.findDocument(doc.getStructTreeRoot());
            if (docElem == null) {
                return "<no Document element>";
            }
            Map<Integer, Set<Content.ContentKind>> contentKinds = new HashMap<>();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                PdfPage page = doc.getPage(i);
                contentKinds.putAll(Content.extractContentKindsForPage(page));
            }
            return StructureTree.toDetailedTreeString(docElem, contentKinds).strip();
        }
    }

    private static String unifiedDiff(String expected, String actual) {
        List<String> goalLines = expected.lines().toList();
        List<String> actualLines = actual.lines().toList();
        Patch<String> patch = DiffUtils.diff(goalLines, actualLines);
        List<String> diff =
                UnifiedDiffUtils.generateUnifiedDiff("goal", "actual", goalLines, patch, 2);
        return String.join("\n", diff);
    }
}
