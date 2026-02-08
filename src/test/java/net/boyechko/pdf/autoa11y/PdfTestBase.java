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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.layout.Document;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

/** Base for tests that optionally persist PDFs via -Dpdf.autoa11y.testOutputDir. */
public abstract class PdfTestBase {
    private static final String ORIGINAL_PDF_SUFFIX = "_before.pdf";
    private static final String BROKEN_PDF_SUFFIX = "_broken.pdf";
    private static final String REMEDIATED_PDF_SUFFIX = "_remediated.pdf";

    @TempDir Path tempDir;
    private String configuredOutputDir;
    private Path outputDir;
    private String testClassName;
    private String testMethodName;

    // ── Test lifecycle ──────────────────────────────────────────────

    @BeforeEach
    void captureTestName(TestInfo testInfo) {
        testClassName =
                testInfo.getTestClass()
                        .map(Class::getSimpleName)
                        .orElse(getClass().getSimpleName());
        testMethodName = testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
    }

    // ── Output path helpers ─────────────────────────────────────────

    protected final OutputStream testOutputStream() {
        String methodName = testMethodName != null ? testMethodName : "test";
        return testOutputStream(methodName + ".pdf");
    }

    protected final OutputStream testOutputStream(String filename) {
        Path outputPath = testOutputPath(filename);
        try {
            return Files.newOutputStream(outputPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create test output file: " + outputPath, e);
        }
    }

    protected final Path testOutputPath() {
        String methodName = testMethodName != null ? testMethodName : "test";
        return testOutputDir().resolve(methodName + ".pdf");
    }

    protected final Path testOutputPath(String filename) {
        return testOutputDir().resolve(filename);
    }

    /** Returns {baseDir}/{testClassName}/, creating it if needed. */
    protected final Path testOutputDir() {
        if (outputDir != null) {
            return outputDir;
        }

        Path baseDir = isPersistentOutputEnabled() ? Path.of(configuredOutputDir()) : tempDir;
        String className = testClassName != null ? testClassName : getClass().getSimpleName();
        Path dir = baseDir.resolve(className);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create test output dir: " + dir, e);
        }
        outputDir = dir;
        return outputDir;
    }

    // ── PDF creation ────────────────────────────────────────────────

    /** Callback for adding content to a test PDF via the iText layout API. */
    @FunctionalInterface
    protected interface TestPdfContent {
        void addTo(PdfDocument pdfDoc, Document document) throws Exception;
    }

    /** Creates a tagged PDF at {@code testOutputPath()} with the given content. */
    protected final Path createTestPdf(TestPdfContent content) throws Exception {
        return createTestPdf(testOutputPath(), content);
    }

    /** Creates a tagged PDF at the specified path with the given content. */
    protected final Path createTestPdf(Path outputPath, TestPdfContent content) throws Exception {
        try (PdfWriter writer = new PdfWriter(outputPath.toString());
                PdfDocument pdfDoc = new PdfDocument(writer)) {
            pdfDoc.setTagged();
            Document document = new Document(pdfDoc);
            content.addTo(pdfDoc, document);
            document.close();
        }
        return outputPath;
    }

    // ── Tag breakage ────────────────────────────────────────────────

    /** Known tag structure breakages that can be applied to a valid tagged PDF. */
    protected enum TagBreakage {
        /** L > P instead of L > LI > LBody > P */
        L_WITH_P_CHILDREN(PdfName.L, PdfTestBase::breakListWithParagraphChildren),

        /** LI > P instead of LI > Lbl + LBody */
        LI_WITH_SINGLE_P(PdfName.LI, PdfTestBase::breakListItemWithSingleParagraph),

        /** LI > Lbl with no LBody */
        MISSING_LBODY(PdfName.LI, PdfTestBase::breakListItemMissingLBody);

        private final PdfName targetRole;
        private final Consumer<PdfStructElem> modifier;

        TagBreakage(PdfName targetRole, Consumer<PdfStructElem> modifier) {
            this.targetRole = targetRole;
            this.modifier = modifier;
        }

        void applyTo(PdfStructTreeRoot root) {
            PdfStructElem target = findFirstByRole(root, targetRole);
            if (target != null) {
                modifier.accept(target);
            }
        }
    }

    /**
     * Creates a valid tagged PDF, then breaks its structure. Saves both:
     *
     * <ul>
     *   <li>{@code {method}{ORIGINAL_PDF_SUFFIX}.pdf} - the valid version
     *   <li>{@code {method}{BROKEN_PDF_SUFFIX}.pdf} - the broken version (returned)
     * </ul>
     */
    protected final Path breakTestPdf(TestPdfContent content, TagBreakage breakage)
            throws Exception {
        String method = testMethodName != null ? testMethodName : "test";
        Path original = createTestPdf(testOutputPath(method + ORIGINAL_PDF_SUFFIX), content);

        Path broken = testOutputPath(method + BROKEN_PDF_SUFFIX);
        try (PdfReader reader = new PdfReader(original.toString());
                PdfWriter writer = new PdfWriter(broken.toString());
                PdfDocument pdfDoc = new PdfDocument(reader, writer)) {
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            if (root != null) {
                breakage.applyTo(root);
            }
        }
        return broken;
    }

    /**
     * Copies the remediated PDF to {@code {method}{REMEDIATED_PDF_SUFFIX}.pdf} in the test output
     * directory.
     */
    protected final void saveRemediatedPdf(ProcessingResult result) throws Exception {
        if (result.tempOutputFile() != null && Files.exists(result.tempOutputFile())) {
            String method = testMethodName != null ? testMethodName : "test";
            Files.copy(
                    result.tempOutputFile(),
                    testOutputPath(method + REMEDIATED_PDF_SUFFIX),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ── Structure tree helpers ──────────────────────────────────────

    protected final PdfStructElem findNthByRole(PdfStructTreeRoot root, PdfName role, int index) {
        List<PdfStructElem> matches = new ArrayList<>();
        for (IStructureNode kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                collectByRole(elem, role, matches);
            }
        }
        return matches.get(index);
    }

    private static PdfStructElem findFirstByRole(PdfStructTreeRoot root, PdfName targetRole) {
        for (Object kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                PdfStructElem found = findByRole(elem, targetRole);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static PdfStructElem findByRole(PdfStructElem elem, PdfName targetRole) {
        if (targetRole.equals(elem.getRole())) return elem;
        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem child) {
                PdfStructElem found = findByRole(child, targetRole);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static PdfStructElem findFirstChild(PdfStructElem parent, PdfName targetRole) {
        for (Object kid : parent.getKids()) {
            if (kid instanceof PdfStructElem kidElem) {
                if (targetRole.equals(kidElem.getRole())) return kidElem;
            }
        }
        return null;
    }

    private void collectByRole(PdfStructElem elem, PdfName role, List<PdfStructElem> out) {
        if (role.equals(elem.getRole())) {
            out.add(elem);
        }
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem child) {
                collectByRole(child, role, out);
            }
        }
    }

    // ── Breakage implementations ────────────────────────────────────

    /** Moves P children out of LI/LBody and directly under L. */
    private static void breakListWithParagraphChildren(PdfStructElem listElem) {
        List<IStructureNode> kids = new ArrayList<>(listElem.getKids());
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem kidElem && PdfName.LI.equals(kidElem.getRole())) {
                PdfStructElem pElem = findParagraphInListItem(kidElem);
                if (pElem != null) {
                    listElem.removeKid(kidElem);
                    listElem.addKid(pElem);
                }
            }
        }
    }

    /** Strips LBody/Lbl from LI, leaving a bare P child. */
    private static void breakListItemWithSingleParagraph(PdfStructElem liElem) {
        List<IStructureNode> kids = new ArrayList<>(liElem.getKids());
        PdfStructElem pElem = null;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem kidElem) {
                if (PdfName.LBody.equals(kidElem.getRole())) {
                    pElem = findFirstChild(kidElem, PdfName.P);
                    if (pElem != null) kidElem.removeKid(pElem);
                }
                liElem.removeKid(kidElem);
            }
        }
        if (pElem != null) liElem.addKid(pElem);
    }

    /** Removes LBody from LI, keeping only Lbl. */
    private static void breakListItemMissingLBody(PdfStructElem liElem) {
        List<IStructureNode> kids = new ArrayList<>(liElem.getKids());
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem kidElem && PdfName.LBody.equals(kidElem.getRole())) {
                liElem.removeKid(kidElem);
            }
        }
    }

    private static PdfStructElem findParagraphInListItem(PdfStructElem liElem) {
        for (Object kid : liElem.getKids()) {
            if (kid instanceof PdfStructElem kidElem && PdfName.LBody.equals(kidElem.getRole())) {
                return findFirstChild(kidElem, PdfName.P);
            }
        }
        return null;
    }

    // ── Configuration ───────────────────────────────────────────────

    private boolean isPersistentOutputEnabled() {
        String configured = configuredOutputDir();
        return configured != null && !configured.isBlank();
    }

    private String configuredOutputDir() {
        if (configuredOutputDir == null) {
            configuredOutputDir = System.getProperty("pdf.autoa11y.testOutputDir");
        }
        return configuredOutputDir;
    }
}
