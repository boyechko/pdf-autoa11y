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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String className = testClassName != null ? testClassName : getClass().getSimpleName();
        String methodName = testMethodName != null ? testMethodName : "test";
        String fileName = String.format("%s-%s.pdf", className, methodName);
        return testOutputStream(fileName);
    }

    protected final OutputStream testOutputStream(String filename) {
        if (!isPersistentOutputEnabled()) {
            return new ByteArrayOutputStream();
        }
        Path outputPath = testOutputPath(filename);
        try {
            return Files.newOutputStream(outputPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create test output file: " + outputPath, e);
        }
    }

    protected final Path testOutputDir() {
        if (outputDir != null) {
            return outputDir;
        }

        if (!isPersistentOutputEnabled()) {
            outputDir = tempDir;
            return outputDir;
        }

        Path dir = Path.of(configuredOutputDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create test output dir: " + dir, e);
        }
        outputDir = dir;
        return outputDir;
    }

    protected final Path testOutputPath(String filename) {
        return testOutputDir().resolve(filename);
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
}
