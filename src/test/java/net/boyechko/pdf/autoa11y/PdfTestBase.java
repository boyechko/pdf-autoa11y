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
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.layout.Document;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

/** Base for tests that optionally persist PDFs via -Dpdf.autoa11y.testOutputDir. */
public abstract class PdfTestBase {
    private static final String REMEDIATED_PDF_SUFFIX = "_remediated.pdf";

    @TempDir Path tempDir;
    private String configuredOutputDir;
    private Path outputDir;
    private String testClassName;
    private String testMethodName;

    // == Common Constants ============================================

    protected static final Path TAGGED_BASELINE_PDF =
            Path.of("src/test/resources/tagged_baseline.pdf");

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

    /**
     * Callback for adding content to a test PDF via iText's high-level layout API.
     *
     * @param pdfDoc Low-level PDF document (tag tree, pages, objects).
     * @param layoutDoc High-level layout root used to add content elements.
     */
    @FunctionalInterface
    protected interface TestPdfContent {
        void addTo(PdfDocument pdfDoc, Document layoutDoc) throws Exception;
    }

    /**
     * Callback for low-level structured PDF fixtures with common scaffolding pre-created.
     *
     * @param pdfDoc Low-level PDF document.
     * @param firstPage First page created by the fixture helper.
     * @param structTreeRoot Structure tree root.
     * @param documentElem Root Document structure element.
     */
    @FunctionalInterface
    protected interface StructuredTestPdfContent {
        void addTo(
                PdfDocument pdfDoc,
                PdfPage firstPage,
                PdfStructTreeRoot structTreeRoot,
                PdfStructElem documentElem)
                throws Exception;
    }

    /** Creates a tagged PDF at {@code testOutputPath()} with the given content. */
    protected final Path createTestPdf(TestPdfContent content) throws Exception {
        return createTestPdf(testOutputPath(), content);
    }

    /** Creates a tagged PDF at the specified path with the given content. */
    protected final Path createTestPdf(Path outputPath, TestPdfContent content) throws Exception {
        try (PdfWriter writer = new PdfWriter(outputPath.toString());
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document layoutDoc = new Document(pdfDoc)) {
            pdfDoc.setTagged();
            content.addTo(pdfDoc, layoutDoc);
        }
        return outputPath;
    }

    /**
     * Creates a tagged PDF with common low-level structure scaffold: one initial page, structure
     * tree root, and top-level Document element.
     */
    protected final Path createStructuredTestPdf(StructuredTestPdfContent content)
            throws Exception {
        return createStructuredTestPdf(testOutputPath(), content);
    }

    /** Creates a structured tagged PDF at the specified path. */
    protected final Path createStructuredTestPdf(Path outputPath, StructuredTestPdfContent content)
            throws Exception {
        try (PdfWriter writer = new PdfWriter(outputPath.toString());
                PdfDocument pdfDoc = new PdfDocument(writer)) {
            pdfDoc.setTagged();
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfStructTreeRoot structTreeRoot = pdfDoc.getStructTreeRoot();
            PdfStructElem documentElem = new PdfStructElem(pdfDoc, PdfName.Document);
            structTreeRoot.addKid(documentElem);
            content.addTo(pdfDoc, firstPage, structTreeRoot, documentElem);
        }
        return outputPath;
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
