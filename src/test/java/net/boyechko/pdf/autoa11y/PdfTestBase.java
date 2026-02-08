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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

/** Base for tests that optionally persist PDFs via -Dpdf.autoa11y.testOutputDir. */
public abstract class PdfTestBase {
    @TempDir Path tempDir;
    private String configuredOutputDir;
    private Path outputDir;
    private String testClassName;
    private String testMethodName;

    @BeforeEach
    void captureTestName(TestInfo testInfo) {
        testClassName =
                testInfo.getTestClass()
                        .map(Class::getSimpleName)
                        .orElse(getClass().getSimpleName());
        testMethodName = testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
    }

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

    protected final PdfStructElem findNthByRole(PdfStructTreeRoot root, PdfName role, int index) {
        List<PdfStructElem> matches = new ArrayList<>();
        for (IStructureNode kid : root.getKids()) {
            if (kid instanceof PdfStructElem elem) {
                collectByRole(elem, role, matches);
            }
        }
        return matches.get(index);
    }

    /** Recursively collects all elements with the given role into the output list. */
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
}
