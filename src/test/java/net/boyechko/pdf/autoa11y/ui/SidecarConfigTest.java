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
        Files.writeString(config, "MistaggedArtifactCheck:\n  page-number: '^Page \\d+$'\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checks().isEmpty());
    }

    @Test
    void loadsCheckConfigByClassName() throws IOException {
        Path pdf = tempDir.resolve("rolemap.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("rolemap.autoa11y.yaml");
        Files.writeString(
                config,
                """
                ReplaceRoleMapCheck:
                  CustomHeading: H1
                  FigureAlt: Figure
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        Map<String, Map<String, String>> configs = sidecar.checkConfigs();
        assertTrue(configs.containsKey("ReplaceRoleMapCheck"));
        assertEquals(
                Map.of("CustomHeading", "H1", "FigureAlt", "Figure"),
                configs.get("ReplaceRoleMapCheck"));
    }

    @Test
    void loadsMultipleCheckConfigs() throws IOException {
        Path pdf = tempDir.resolve("multi.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("multi.autoa11y.yaml");
        Files.writeString(
                config,
                """
                checks:
                  - MistaggedArtifactCheck
                  - ReplaceRoleMapCheck

                MistaggedArtifactCheck:
                  page-number: '^Page \\d+$'

                ReplaceRoleMapCheck:
                  CustomHeading: H1
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        Map<String, Map<String, String>> configs = sidecar.checkConfigs();
        assertEquals(2, configs.size());
        assertEquals(Map.of("page-number", "^Page \\d+$"), configs.get("MistaggedArtifactCheck"));
        assertEquals(Map.of("CustomHeading", "H1"), configs.get("ReplaceRoleMapCheck"));
    }

    @Test
    void loadsCheckConfigInListForm() throws IOException {
        Path pdf = tempDir.resolve("listcfg.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("listcfg.autoa11y.yaml");
        Files.writeString(
                config,
                """
                ReorderWebCapturesCheck:
                  - www.uwb.edu/catalog
                  - www.uwb.edu/catalog/degree-programs
                  - www.washington.edu/students/crscatb
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        // List entries become entries keyed by "0", "1", "2", ... in YAML order.
        Map<String, String> cfg = sidecar.checkConfigs().get("ReorderWebCapturesCheck");
        assertNotNull(cfg);
        assertEquals(3, cfg.size());
        assertEquals(
                List.of(
                        "www.uwb.edu/catalog",
                        "www.uwb.edu/catalog/degree-programs",
                        "www.washington.edu/students/crscatb"),
                List.copyOf(cfg.values()));
    }

    @Test
    void emptyCheckConfigYieldsEmptyMap() throws IOException {
        Path pdf = tempDir.resolve("emptycfg.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("emptycfg.autoa11y.yaml");
        Files.writeString(config, "MistaggedArtifactCheck:\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checkConfigs().containsKey("MistaggedArtifactCheck"));
        assertTrue(sidecar.checkConfigs().get("MistaggedArtifactCheck").isEmpty());
    }

    @Test
    void unknownTopLevelKeyIsIgnored() throws IOException {
        Path pdf = tempDir.resolve("unknown.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("unknown.autoa11y.yaml");
        Files.writeString(config, "NotARealCheck:\n  foo: bar\n");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        // Unknown keys are not propagated; the warning goes to the log only.
        assertTrue(sidecar.isPresent());
        assertTrue(sidecar.checkConfigs().isEmpty());
    }

    @Test
    void returnsEmptyWhenNoSidecarFileExists() {
        Path pdf = tempDir.resolve("no-config.pdf");

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        assertTrue(sidecar.checks().isEmpty());
        assertTrue(sidecar.checkConfigs().isEmpty());
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
        assertTrue(sidecar.checkConfigs().isEmpty());
    }

    @Test
    void legacyKeysAreIgnored() throws IOException {
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
                role-map: clear
                artifact-patterns:
                  page-number: '^Page \\d+$'
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        // Legacy keys are not consumed; they generate a warning but no checks/checkConfigs are set.
        assertTrue(sidecar.isPresent());
        assertTrue(sidecar.checks().isEmpty());
        assertTrue(sidecar.checkConfigs().isEmpty());
    }

    @Test
    void loadsArtifactPatternsViaMistaggedArtifactCheckSideKey() throws IOException {
        Path pdf = tempDir.resolve("patterns.pdf");
        Files.createFile(pdf);
        Path config = tempDir.resolve("patterns.autoa11y.yaml");
        Files.writeString(
                config,
                """
                MistaggedArtifactCheck:
                  page-number: '^\\s*(Page\\s+)?\\d+\\s*$'
                  chapter-header: 'Chapter \\d+'
                """);

        SidecarConfig sidecar = SidecarConfig.forPdf(pdf);

        Map<String, String> patterns = sidecar.checkConfigs().get("MistaggedArtifactCheck");
        assertNotNull(patterns);
        assertEquals(2, patterns.size());
        assertEquals("^\\s*(Page\\s+)?\\d+\\s*$", patterns.get("page-number"));
        assertEquals("Chapter \\d+", patterns.get("chapter-header"));
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
