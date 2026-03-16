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

    private SidecarConfig(
            boolean present,
            Set<String> skipChecks,
            Set<String> onlyChecks,
            Set<String> includeChecks,
            Optional<Map<String, String>> roleMap) {
        this.present = present;
        this.skipChecks = Set.copyOf(skipChecks);
        this.onlyChecks = Set.copyOf(onlyChecks);
        this.includeChecks = Set.copyOf(includeChecks);
        this.roleMap = roleMap;
    }

    private static SidecarConfig empty() {
        return new SidecarConfig(false, Set.of(), Set.of(), Set.of(), Optional.empty());
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
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);
            if (data == null) {
                return new SidecarConfig(true, Set.of(), Set.of(), Set.of(), Optional.empty());
            }
            Set<String> skip = extractStringList(data, "skip-checks");
            Set<String> only = extractStringList(data, "only-checks");
            Set<String> include = extractStringList(data, "include-checks");
            Optional<Map<String, String>> roleMap = extractRoleMap(data);
            return new SidecarConfig(true, skip, only, include, roleMap);
        }
    }

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
