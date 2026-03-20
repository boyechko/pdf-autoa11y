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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SidecarConfigTest {

    @TempDir Path tempDir;

    @Test
    void loadsSkipChecksFromSidecarFile() throws IOException {
        Path pdf = tempDir.resolve("document.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("document.autoa11y.yaml");
        Files.writeString(
                config,
                """
                skip-checks:
                  - NeedlessNestingCheck
                  - MissingPagePartsCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertEquals(Set.of("NeedlessNestingCheck", "MissingPagePartsCheck"), sidecar.skipChecks());
    }

    @Test
    void loadsOnlyChecksFromSidecarFile() throws IOException {
        Path pdf = tempDir.resolve("textbook.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("textbook.autoa11y.yaml");
        Files.writeString(
                config,
                """
                only-checks:
                  - SchemaValidationCheck
                  - EmptyElementCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertEquals(Set.of("SchemaValidationCheck", "EmptyElementCheck"), sidecar.onlyChecks());
    }

    @Test
    void loadsIncludeChecksFromSidecarFile() throws IOException {
        Path pdf = tempDir.resolve("rolemap.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("rolemap.autoa11y.yaml");
        Files.writeString(
                config,
                """
                include-checks:
                  - ClearRoleMapCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertEquals(Set.of("ClearRoleMapCheck"), sidecar.includeChecks());
    }

    @Test
    void mergesWithAdditionalIncludeChecks() throws IOException {
        Path pdf = tempDir.resolve("mergeincl.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("mergeincl.autoa11y.yaml");
        Files.writeString(
                config,
                """
                include-checks:
                  - ClearRoleMapCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);
        Set<String> additional = Set.of("ReplaceRoleMapCheck");
        Set<String> merged = sidecar.mergeIncludeChecks(additional);

        assertEquals(Set.of("ClearRoleMapCheck", "ReplaceRoleMapCheck"), merged);
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
        Files.writeString(config, "skip-checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.roleMap().isEmpty());
    }

    @Test
    void returnsEmptySetsWhenNoSidecarFileExists() {
        Path pdf = tempDir.resolve("no-config.pdf");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.skipChecks().isEmpty());
        assertTrue(sidecar.onlyChecks().isEmpty());
        assertTrue(sidecar.includeChecks().isEmpty());
        assertFalse(sidecar.isPresent());
    }

    @Test
    void isPresentWhenSidecarFileExists() throws IOException {
        Path pdf = tempDir.resolve("doc.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("doc.autoa11y.yaml");
        Files.writeString(config, "skip-checks:\n  - EmptyElementCheck\n");

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
        assertTrue(sidecar.skipChecks().isEmpty());
        assertTrue(sidecar.onlyChecks().isEmpty());
        assertTrue(sidecar.includeChecks().isEmpty());
    }

    @Test
    void handlesBothSkipAndOnlyChecks() throws IOException {
        Path pdf = tempDir.resolve("both.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("both.autoa11y.yaml");
        Files.writeString(
                config,
                """
                skip-checks:
                  - NeedlessNestingCheck
                only-checks:
                  - SchemaValidationCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertEquals(Set.of("NeedlessNestingCheck"), sidecar.skipChecks());
        assertEquals(Set.of("SchemaValidationCheck"), sidecar.onlyChecks());
    }

    @Test
    void mergesWithAdditionalSkipChecks() throws IOException {
        Path pdf = tempDir.resolve("merge.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("merge.autoa11y.yaml");
        Files.writeString(
                config,
                """
                skip-checks:
                  - NeedlessNestingCheck
                  - MissingPagePartsCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);
        Set<String> additional = Set.of("EmptyElementCheck");
        Set<String> merged = sidecar.mergeSkipChecks(additional);

        assertEquals(
                Set.of("NeedlessNestingCheck", "MissingPagePartsCheck", "EmptyElementCheck"),
                merged);
    }

    @Test
    void mergesWithAdditionalOnlyChecks() throws IOException {
        Path pdf = tempDir.resolve("merge2.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("merge2.autoa11y.yaml");
        Files.writeString(
                config,
                """
                only-checks:
                  - SchemaValidationCheck
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);
        Set<String> additional = Set.of("EmptyElementCheck");
        Set<String> merged = sidecar.mergeOnlyChecks(additional);

        assertEquals(Set.of("SchemaValidationCheck", "EmptyElementCheck"), merged);
    }

    @Test
    void additionalChecksAloneWhenNoSidecar() {
        Path pdf = tempDir.resolve("nosidecar.pdf");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);
        Set<String> additional = Set.of("EmptyElementCheck");
        Set<String> merged = sidecar.mergeSkipChecks(additional);

        assertEquals(Set.of("EmptyElementCheck"), merged);
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
        Files.writeString(config, "skip-checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.artifactPatterns().isEmpty());
    }

    @Test
    void stripsAutoa11ySuffixWhenLookingForConfig() throws IOException {
        Path pdf = tempDir.resolve("textbook_autoa11y.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("textbook.autoa11y.yaml");
        Files.writeString(config, "skip-checks:\n  - EmptyElementCheck\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.isPresent());
        assertEquals(Set.of("EmptyElementCheck"), sidecar.skipChecks());
    }
}
