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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.core.NoOpProcessingListener;
import net.boyechko.pdf.autoa11y.core.ProcessingDefaults;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.core.ProcessingService;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.tools.TreeDiagram;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Goal-driven integration tests. Each test runs the remediation pipeline — either all default
 * checks or an explicit subset — on a real PDF and compares the output structure tree against a
 * hand-crafted goal snapshot.
 *
 * <p>Workflow for adding a new test case:
 *
 * <ol>
 *   <li>Run {@code ./pdf-autoa11y --dump-tree inputs/foo.pdf} to see the "before" tree
 *   <li>Manually remediate the PDF (e.g., in Acrobat)
 *   <li>Run {@code ./pdf-autoa11y --dump-tree=plain foo_fixed.pdf >
 *       src/test/resources/foo.goal.txt}
 *   <li>Run tests — the new case is discovered from the goal file and fails (red) until automated
 *       fixes match the goal (green)
 * </ol>
 *
 * <p>Test cases are discovered by scanning {@code src/test/resources} for {@code
 * <base>[.<Check1+Check2>].goal.txt}: the optional check list scopes the run to those checks via
 * {@code onlyChecks} (otherwise all default checks run), and {@code <base>.pdf} is the input. So
 * {@code foo.MistaggedHeadingCheck.goal.txt} exercises only that check against {@code foo.pdf}.
 */
public class GoalDrivenIntegrationTest extends PdfTestBase {

    private static final Path RESOURCES_DIR = Path.of("src/test/resources");

    private static final String GOAL_SUFFIX = ".goal.txt";

    record GoalCase(String pdfName, List<String> checks) {
        String goalName() {
            String baseName = pdfName.replace(".pdf", "");
            String suffix = checks.isEmpty() ? "" : "." + String.join("+", checks);
            return baseName + suffix + GOAL_SUFFIX;
        }

        @Override
        public String toString() {
            return checks.isEmpty() ? pdfName : pdfName + " [" + String.join(", ", checks) + "]";
        }
    }

    static List<GoalCase> goalCases() throws Exception {
        try (var files = Files.list(RESOURCES_DIR)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(GOAL_SUFFIX))
                    .sorted()
                    .map(GoalDrivenIntegrationTest::parseGoalFileName)
                    .toList();
        }
    }

    /**
     * Parses {@code <base>[.<Check1+Check2>].goal.txt} into a case targeting {@code <base>.pdf}.
     */
    private static GoalCase parseGoalFileName(String goalName) {
        String rest = goalName.substring(0, goalName.length() - GOAL_SUFFIX.length());
        // A dot in the base name itself is disambiguated by whether the full-name PDF exists
        int dot = rest.lastIndexOf('.');
        if (dot < 0 || Files.exists(RESOURCES_DIR.resolve(rest + ".pdf"))) {
            return new GoalCase(rest + ".pdf", List.of());
        }
        return new GoalCase(
                rest.substring(0, dot) + ".pdf", List.of(rest.substring(dot + 1).split("\\+")));
    }

    /** Fails on check names {@code selectChecks} would silently ignore, emptying the pipeline. */
    private static void requireKnownChecks(List<String> checks) {
        Set<String> known =
                ProcessingDefaults.allChecks().stream()
                        .map(supplier -> supplier.get().getClass().getSimpleName())
                        .collect(Collectors.toSet());
        List<String> unknown = checks.stream().filter(c -> !known.contains(c)).toList();
        if (!unknown.isEmpty()) {
            fail("Unknown check(s) in goal file name: " + unknown);
        }
    }

    @ParameterizedTest(name = "remediate {0}")
    @MethodSource("goalCases")
    @Tag("GoalDriven")
    void remediateAndCompareToGoal(GoalCase goalCase) throws Exception {
        Path inputPath = RESOURCES_DIR.resolve(goalCase.pdfName());
        assumeTrue(Files.exists(inputPath), "Input PDF not found: " + inputPath);
        requireKnownChecks(goalCase.checks());

        Path goalFile = RESOURCES_DIR.resolve(goalCase.goalName());

        ProcessingService.ProcessingServiceBuilder builder =
                new ProcessingService.ProcessingServiceBuilder()
                        .withPdfCustodian(new PdfCustodian(inputPath))
                        .withListener(new NoOpProcessingListener());
        if (!goalCase.checks().isEmpty()) {
            builder.onlyChecks(new HashSet<>(goalCase.checks()));
        }
        ProcessingService service = builder.build();

        ProcessingResult result = service.remediate();
        saveRemediatedPdf(result);

        // Remove obj numbers (#) and MCID (&) from tree to make it easier to compare
        String actualTree = readStructureTree(result.tempOutputFile()).replaceAll(" [#&]\\d+", "");
        String expectedTree = Files.readString(goalFile).strip().replaceAll(" [#&]\\d+", "");

        if (!expectedTree.equals(actualTree)) {
            fail(
                    "Structure tree of remediated "
                            + goalCase
                            + " does not match goal.\n"
                            + "To update the goal, manually remediate the PDF and run:\n"
                            + "  ./pdf-autoa11y --dump-tree=plain <fixed.pdf> > "
                            + goalFile
                            + "\n\n"
                            + unifiedDiff(expectedTree, actualTree));
        }
    }

    private String readStructureTree(Path pdfPath) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            PdfStructElem docElem = StructTree.findDocument(doc.getStructTreeRoot());
            if (docElem == null) {
                return "<no Document element>";
            }
            Map<Content.PageMcid, Content.McidContent> mcidInfo = new HashMap<>();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                PdfPage page = doc.getPage(i);
                int pageNum = i;
                Content.extractContentForPage(page)
                        .forEach(
                                (mcid, mc) ->
                                        mcidInfo.put(new Content.PageMcid(pageNum, mcid), mc));
            }
            return TreeDiagram.toDetailedTreeString(docElem, mcidInfo, TreeDiagram.Style.PLAIN)
                    .strip();
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
