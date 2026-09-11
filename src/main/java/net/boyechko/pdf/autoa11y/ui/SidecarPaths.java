// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import java.nio.file.Path;

/**
 * Resolves per-document sidecar files (config, remediation log) that sit beside the PDF.
 *
 * <p>The name is derived from the document's <em>lineage</em> base rather than its filename:
 * trailing {@code _autoa11y} suffixes are stripped, so {@code catalog.pdf} and the {@code
 * catalog_autoa11y.pdf} it produces resolve to the same sidecar. Without that, re-running the tool
 * on its own output would fork the sidecar and lose continuity.
 */
public final class SidecarPaths {
    private static final String LINEAGE_BASE = "(_autoa11y)*\\.[^.]+$";

    private SidecarPaths() {}

    /**
     * Returns the sidecar path for {@code pdfPath} with the given extension (e.g. ".autoa11y.log").
     */
    public static Path forPdf(Path pdfPath, String extension) {
        String baseName = pdfPath.getFileName().toString().replaceFirst(LINEAGE_BASE, "");
        Path parent = pdfPath.getParent();
        String sidecarName = baseName + extension;
        return parent != null ? parent.resolve(sidecarName) : Path.of(sidecarName);
    }
}
