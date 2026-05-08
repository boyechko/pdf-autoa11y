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
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SidecarConfigTest {

    @TempDir Path tempDir;

    @Test
    void loadsOrderedChecksFromSidecarFile() throws IOException {
        Path pdf = tempDir.resolve("document.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("document.autoa11y.yaml");
        Files.writeString(
                config,
                """
                checks:
                  - SchemaValidationCheck
                  - EmptyElementCheck
                  - NeedlessNestingCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checks().isPresent());
        assertEquals(
                List.of("SchemaValidationCheck", "EmptyElementCheck", "NeedlessNestingCheck"),
                sidecar.checks().get());
    }

    @Test
    void preservesChecksOrderAcrossLoad() throws IOException {
        Path pdf = tempDir.resolve("ordered.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("ordered.autoa11y.yaml");
        Files.writeString(
                config,
                """
                checks:
                  - WrapWebCapturesCheck
                  - InlineDestinationsCheck
                  - SchemaValidationCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertEquals(
                List.of("WrapWebCapturesCheck", "InlineDestinationsCheck", "SchemaValidationCheck"),
                sidecar.checks().get());
    }

    @Test
    void emptyChecksListMeansRunNothing() throws IOException {
        Path pdf = tempDir.resolve("empty.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("empty.autoa11y.yaml");
        Files.writeString(config, "checks: []\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checks().isPresent());
        assertTrue(sidecar.checks().get().isEmpty());
    }

    @Test
    void checksAbsentWhenNotSpecified() throws IOException {
        Path pdf = tempDir.resolve("none.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("none.autoa11y.yaml");
        Files.writeString(config, "role-map: clear\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checks().isEmpty());
    }

    @Test
    void loadsRoleMapMappingsFromSidecarFile() throws IOException {
        Path pdf = tempDir.resolve("rolemapmappings.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("rolemapmappings.autoa11y.yaml");
        Files.writeString(
                config,
                """
                role-map:
                  CustomHeading: H1
                  FigureAlt: Figure
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.roleMap().isPresent());
        assertEquals(Map.of("CustomHeading", "H1", "FigureAlt", "Figure"), sidecar.roleMap().get());
    }

    @Test
    void roleMapClearReturnsEmptyMap() throws IOException {
        Path pdf = tempDir.resolve("clearrm.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("clearrm.autoa11y.yaml");
        Files.writeString(config, "role-map: clear\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.roleMap().isPresent());
        assertTrue(sidecar.roleMap().get().isEmpty());
    }

    @Test
    void roleMapAbsentWhenNotSpecified() throws IOException {
        Path pdf = tempDir.resolve("normnomap.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("normnomap.autoa11y.yaml");
        Files.writeString(config, "checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.roleMap().isEmpty());
    }

    @Test
    void returnsEmptyWhenNoSidecarFileExists() {
        Path pdf = tempDir.resolve("no-config.pdf");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checks().isEmpty());
        assertTrue(sidecar.roleMap().isEmpty());
        assertTrue(sidecar.artifactPatterns().isEmpty());
        assertFalse(sidecar.isPresent());
    }

    @Test
    void isPresentWhenSidecarFileExists() throws IOException {
        Path pdf = tempDir.resolve("doc.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("doc.autoa11y.yaml");
        Files.writeString(config, "checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.isPresent());
    }

    @Test
    void handlesEmptyConfigFile() throws IOException {
        Path pdf = tempDir.resolve("empty.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("empty.autoa11y.yaml");
        Files.writeString(config, "");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.isPresent());
        assertTrue(sidecar.checks().isEmpty());
        assertTrue(sidecar.roleMap().isEmpty());
        assertTrue(sidecar.artifactPatterns().isEmpty());
    }

    @Test
    void legacySkipChecksKeyIsIgnored() throws IOException {
        Path pdf = tempDir.resolve("legacy.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("legacy.autoa11y.yaml");
        Files.writeString(
                config,
                """
                skip-checks:
                  - NeedlessNestingCheck
                only-checks:
                  - SchemaValidationCheck
                include-checks:
                  - ClearRoleMapCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        // Legacy keys are not consumed; they generate a warning but no `checks:` is set.
        assertTrue(sidecar.isPresent());
        assertTrue(sidecar.checks().isEmpty());
    }

    @Test
    void loadsArtifactPatternsFromSidecarFile() throws IOException {
        Path pdf = tempDir.resolve("patterns.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("patterns.autoa11y.yaml");
        Files.writeString(
                config,
                """
                artifact-patterns:
                  page-number: '^\\s*(Page\\s+)?\\d+\\s*$'
                  chapter-header: 'Chapter \\d+'
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.artifactPatterns().isPresent());
        Map<String, String> patterns = sidecar.artifactPatterns().get();
        assertEquals(2, patterns.size());
        assertEquals("^\\s*(Page\\s+)?\\d+\\s*$", patterns.get("page-number"));
        assertEquals("Chapter \\d+", patterns.get("chapter-header"));
    }

    @Test
    void emptyArtifactPatternsReturnsEmptyMap() throws IOException {
        Path pdf = tempDir.resolve("emptypatterns.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("emptypatterns.autoa11y.yaml");
        Files.writeString(config, "artifact-patterns:\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.artifactPatterns().isPresent());
        assertTrue(sidecar.artifactPatterns().get().isEmpty());
    }

    @Test
    void artifactPatternsAbsentWhenNotSpecified() throws IOException {
        Path pdf = tempDir.resolve("nopatterns.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("nopatterns.autoa11y.yaml");
        Files.writeString(config, "checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.artifactPatterns().isEmpty());
    }

    @Test
    void stripsAutoa11ySuffixWhenLookingForConfig() throws IOException {
        Path pdf = tempDir.resolve("textbook_autoa11y.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("textbook.autoa11y.yaml");
        Files.writeString(config, "checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.isPresent());
        assertEquals(List.of("EmptyElementCheck"), sidecar.checks().get());
    }
}
