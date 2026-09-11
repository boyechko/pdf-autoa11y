// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SidecarPathsTest {

    @Test
    void resolvesSiblingNamedAfterDocumentBase() {
        Path sidecar = SidecarPaths.forPdf(Path.of("/docs/catalog.pdf"), ".autoa11y.log");

        assertEquals(Path.of("/docs/catalog.autoa11y.log"), sidecar);
    }

    @Test
    void remediatedOutputResolvesToSameFileAsItsInput() {
        Path fromInput = SidecarPaths.forPdf(Path.of("/docs/catalog.pdf"), ".autoa11y.log");
        Path fromOutput =
                SidecarPaths.forPdf(Path.of("/docs/catalog_autoa11y.pdf"), ".autoa11y.log");

        assertEquals(fromInput, fromOutput);
    }

    @Test
    void repeatedRemediationSuffixesCollapseToOneFile() {
        Path sidecar =
                SidecarPaths.forPdf(
                        Path.of("/docs/catalog_autoa11y_autoa11y.pdf"), ".autoa11y.log");

        assertEquals(Path.of("/docs/catalog.autoa11y.log"), sidecar);
    }

    @Test
    void suffixInsideTheNameIsNotMistakenForTheTrailingOne() {
        Path sidecar = SidecarPaths.forPdf(Path.of("/docs/autoa11y_notes.pdf"), ".autoa11y.log");

        assertEquals(Path.of("/docs/autoa11y_notes.autoa11y.log"), sidecar);
    }

    @Test
    void bareFilenameResolvesInTheCurrentDirectory() {
        Path sidecar = SidecarPaths.forPdf(Path.of("catalog.pdf"), ".autoa11y.log");

        assertEquals(Path.of("catalog.autoa11y.log"), sidecar);
    }
}
