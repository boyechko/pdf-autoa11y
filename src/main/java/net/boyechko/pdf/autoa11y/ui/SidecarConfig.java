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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Logger logger = LoggerFactory.getLogger(SidecarConfig.class);

    private final boolean present;
    private final Set<String> skipChecks;
    private final Set<String> onlyChecks;
    private final Set<String> includeChecks;
    private final Optional<Map<String, String>> roleMap;
    private final Optional<Map<String, String>> artifactPatterns;

    private SidecarConfig(Builder builder) {
        this.present = builder.present;
        this.skipChecks = Set.copyOf(builder.skipChecks);
        this.onlyChecks = Set.copyOf(builder.onlyChecks);
        this.includeChecks = Set.copyOf(builder.includeChecks);
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

        sb.append("#skip-checks:\n");
        sb.append("#  - CheckName\n\n");

        sb.append("#only-checks:\n");
        for (Supplier<Check> supplier : ProcessingDefaults.defaultChecks()) {
            sb.append("#  - ").append(supplier.get().getClass().getSimpleName()).append("\n");
        }
        sb.append("\n");

        sb.append("#include-checks:\n");
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

    public Set<String> skipChecks() {
        return skipChecks;
    }

    public Set<String> onlyChecks() {
        return onlyChecks;
    }

    public Set<String> includeChecks() {
        return includeChecks;
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

    // == Merging ==========================================================

    /** Returns the union of sidecar skip-checks and additional skip-checks. */
    public Set<String> mergeSkipChecks(Set<String> additionalSkipChecks) {
        return mergeSets(skipChecks, additionalSkipChecks);
    }

    /** Returns the union of sidecar only-checks and additional only-checks. */
    public Set<String> mergeOnlyChecks(Set<String> additionalOnlyChecks) {
        return mergeSets(onlyChecks, additionalOnlyChecks);
    }

    /** Returns the union of sidecar include-checks and additional include-checks. */
    public Set<String> mergeIncludeChecks(Set<String> additionalIncludeChecks) {
        return mergeSets(includeChecks, additionalIncludeChecks);
    }

    // == Builder ==========================================================

    private static class Builder {
        boolean present;
        Set<String> skipChecks = Set.of();
        Set<String> onlyChecks = Set.of();
        Set<String> includeChecks = Set.of();
        Optional<Map<String, String>> roleMap = Optional.empty();
        Optional<Map<String, String>> artifactPatterns = Optional.empty();

        Builder skipChecks(Set<String> skipChecks) {
            this.skipChecks = skipChecks;
            return this;
        }

        Builder onlyChecks(Set<String> onlyChecks) {
            this.onlyChecks = onlyChecks;
            return this;
        }

        Builder includeChecks(Set<String> includeChecks) {
            this.includeChecks = includeChecks;
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

    @SuppressWarnings("unchecked")
    private static SidecarConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Object> data = new Yaml().load(reader);
            Builder b = new Builder();
            b.present = true;
            if (data != null) {
                b.skipChecks(extractStringList(data, "skip-checks"))
                        .onlyChecks(extractStringList(data, "only-checks"))
                        .includeChecks(extractStringList(data, "include-checks"))
                        .roleMap(extractRoleMap(data))
                        .artifactPatterns(extractStringMap(data, "artifact-patterns"));
            }
            return b.build();
        }
    }

    // == Extraction helpers ===============================================

    @SuppressWarnings("unchecked")
    private static Set<String> extractStringList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?> list) {
            Set<String> result = new LinkedHashSet<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                }
            }
            return result;
        }
        return Set.of();
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

    @SuppressWarnings("unchecked")
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

    private static Set<String> mergeSets(Set<String> a, Set<String> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        Set<String> merged = new LinkedHashSet<>(a);
        merged.addAll(b);
        return Set.copyOf(merged);
    }
}
