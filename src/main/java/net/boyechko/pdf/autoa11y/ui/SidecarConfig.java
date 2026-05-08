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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.boyechko.pdf.autoa11y.core.ProcessingDefaults;
import net.boyechko.pdf.autoa11y.validation.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads per-PDF configuration from a sidecar YAML file. For {@code document.pdf}, looks for {@code
 * document.autoa11y.yaml} in the same directory.
 */
public final class SidecarConfig {
    private static final String SIDECAR_EXTENSION = ".autoa11y.yaml";
    private static final List<String> LEGACY_CHECK_KEYS =
            List.of("skip-checks", "only-checks", "include-checks");
    private static final Logger logger = LoggerFactory.getLogger(SidecarConfig.class);

    private final boolean present;
    private final Optional<List<String>> checks;
    private final Optional<Map<String, String>> roleMap;
    private final Optional<Map<String, String>> artifactPatterns;

    private SidecarConfig(Builder builder) {
        this.present = builder.present;
        this.checks = builder.checks;
        this.roleMap = builder.roleMap;
        this.artifactPatterns = builder.artifactPatterns;
    }

    private static SidecarConfig empty() {
        return new Builder().build();
    }

    /** Loads sidecar config from an explicit path. Throws IOException if the file is missing. */
    public static SidecarConfig fromPath(Path sidecarPath) throws IOException {
        if (!Files.exists(sidecarPath)) {
            throw new IOException("Sidecar config not found: " + sidecarPath);
        }
        logger.info("Loading sidecar config: {}", sidecarPath);
        return load(sidecarPath);
    }

    /** Loads sidecar config for the given PDF path, or returns an empty config if none exists. */
    public static SidecarConfig forPdf(Path pdfPath) {
        Path sidecarPath = resolveSidecarPath(pdfPath);
        if (!Files.exists(sidecarPath)) {
            return empty();
        }
        logger.info("Loading sidecar config: {}", sidecarPath);
        try {
            return load(sidecarPath);
        } catch (IOException e) {
            logger.warn("Failed to read sidecar config {}: {}", sidecarPath, e.getMessage());
            return empty();
        }
    }

    /** Creates a template sidecar config file for the given PDF. Returns the created path. */
    public static Path createTemplate(Path pdfPath) throws IOException {
        Path sidecarPath = resolveSidecarPath(pdfPath);
        StringBuilder sb = new StringBuilder();
        sb.append("# Sidecar config for ").append(pdfPath.getFileName()).append("\n");
        sb.append("# See --help for details.\n\n");

        sb.append("# Checks to run, in order. If absent, the default pipeline runs.\n");
        sb.append("#checks:\n");
        for (Supplier<Check> supplier : ProcessingDefaults.defaultChecks()) {
            sb.append("#  - ").append(supplier.get().getClass().getSimpleName()).append("\n");
        }
        sb.append("# Optional checks (uncomment to enable):\n");
        for (Supplier<Check> supplier : ProcessingDefaults.optionalChecks()) {
            sb.append("#  - ").append(supplier.get().getClass().getSimpleName()).append("\n");
        }
        sb.append("\n");

        sb.append("#role-map:\n");
        sb.append("#  CustomRole: StandardRole\n\n");

        sb.append("#artifact-patterns:\n");
        sb.append("#  pattern-name: 'regex'\n");

        Files.writeString(sidecarPath, sb.toString());
        return sidecarPath;
    }

    // == Accessors ========================================================

    /** Whether a sidecar config file was found. */
    public boolean isPresent() {
        return present;
    }

    /**
     * Ordered list of checks to run, if specified in the sidecar. {@code Optional.empty()} means no
     * {@code checks:} key was set; an empty list means "run nothing".
     */
    public Optional<List<String>> checks() {
        return checks;
    }

    /**
     * Returns the role-map mappings if specified in the sidecar config. An empty map means "clear
     * the role map"; a non-empty map means "replace with these mappings".
     */
    public Optional<Map<String, String>> roleMap() {
        return roleMap;
    }

    /**
     * Returns artifact text patterns if specified in the sidecar config. A map of pattern name to
     * regex. When present, these replace the built-in default patterns.
     */
    public Optional<Map<String, String>> artifactPatterns() {
        return artifactPatterns;
    }

    // == Builder ==========================================================

    private static class Builder {
        boolean present;
        Optional<List<String>> checks = Optional.empty();
        Optional<Map<String, String>> roleMap = Optional.empty();
        Optional<Map<String, String>> artifactPatterns = Optional.empty();

        Builder checks(Optional<List<String>> checks) {
            this.checks = checks;
            return this;
        }

        Builder roleMap(Optional<Map<String, String>> roleMap) {
            this.roleMap = roleMap;
            return this;
        }

        Builder artifactPatterns(Optional<Map<String, String>> artifactPatterns) {
            this.artifactPatterns = artifactPatterns;
            return this;
        }

        SidecarConfig build() {
            return new SidecarConfig(this);
        }
    }

    // == Loading ==========================================================

    private static Path resolveSidecarPath(Path pdfPath) {
        String filename = pdfPath.getFileName().toString();
        String baseName = filename.replaceFirst("(_autoa11y)*\\.[^.]+$", "");
        Path parent = pdfPath.getParent();
        String sidecarName = baseName + SIDECAR_EXTENSION;
        return parent != null ? parent.resolve(sidecarName) : Path.of(sidecarName);
    }

    private static SidecarConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Object> data = new Yaml().load(reader);
            Builder b = new Builder();
            b.present = true;
            if (data != null) {
                warnOnLegacyKeys(path, data);
                b.checks(extractChecks(data))
                        .roleMap(extractRoleMap(data))
                        .artifactPatterns(extractStringMap(data, "artifact-patterns"));
            }
            return b.build();
        }
    }

    private static void warnOnLegacyKeys(Path path, Map<String, Object> data) {
        for (String key : LEGACY_CHECK_KEYS) {
            if (data.containsKey(key)) {
                logger.warn(
                        "Sidecar config {} uses legacy '{}' key, which is no longer supported."
                                + " Replace with a single ordered 'checks:' list.",
                        path,
                        key);
            }
        }
    }

    // == Extraction helpers ===============================================

    private static Optional<List<String>> extractChecks(Map<String, Object> data) {
        if (!data.containsKey("checks")) {
            return Optional.empty();
        }
        Object value = data.get("checks");
        if (value == null) {
            return Optional.of(List.of());
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("checks must be a list of check class names");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return Optional.of(List.copyOf(result));
    }

    /** Extracts an optional String-to-String map from a YAML key. */
    private static Optional<Map<String, String>> extractStringMap(
            Map<String, Object> data, String key) {
        if (!data.containsKey(key)) {
            return Optional.empty();
        }
        Object value = data.get(key);
        if (value == null) {
            return Optional.of(Map.of());
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(key + " must be a mapping of names to values");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String k)) {
                throw new IllegalArgumentException(key + " keys must be strings");
            }
            if (!(entry.getValue() instanceof String v)) {
                throw new IllegalArgumentException(key + " values must be strings");
            }
            result.put(k.trim(), v.trim());
        }
        return Optional.of(Map.copyOf(result));
    }

    private static Optional<Map<String, String>> extractRoleMap(Map<String, Object> data) {
        Object value = data.get("role-map");
        if (value == null) {
            return Optional.empty();
        }
        if ("clear".equals(value)) {
            return Optional.of(Map.of());
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(
                    "role-map must be a mapping of custom role names to standard tags, or 'clear'");
        }
        Map<String, String> mappings = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("role-map keys must be strings");
            }
            if (!(entry.getValue() instanceof String val)) {
                throw new IllegalArgumentException("role-map values must be strings");
            }
            mappings.put(key.trim(), val.trim());
        }
        return Optional.of(Map.copyOf(mappings));
    }
}
