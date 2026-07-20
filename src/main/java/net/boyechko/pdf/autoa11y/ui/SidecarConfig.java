// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *
 * <p>The sidecar has one structural key, {@code checks:}, plus optional per-check configuration
 * keys whose names match check class names (e.g. {@code MistaggedArtifactCheck:} or {@code
 * ReplaceRoleMapCheck:}).
 */
public final class SidecarConfig {
    private static final String SIDECAR_EXTENSION = ".autoa11y.yaml";
    private static final String CHECKS_KEY = "checks";
    private static final List<String> LEGACY_KEYS =
            List.of(
                    "skip-checks",
                    "only-checks",
                    "include-checks",
                    "role-map",
                    "artifact-patterns");
    private static final Logger logger = LoggerFactory.getLogger(SidecarConfig.class);

    private final boolean present;
    private final Optional<List<String>> checks;
    private final Map<String, Map<String, String>> checkConfigs;

    private SidecarConfig(Builder builder) {
        this.present = builder.present;
        this.checks = builder.checks;
        this.checkConfigs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.checkConfigs));
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

        sb.append("# Per-check configuration. Each top-level key matches a check class name.\n");
        sb.append("#MistaggedArtifactCheck:\n");
        sb.append("#  pattern-name: 'regex'\n\n");

        sb.append("#ReplaceRoleMapCheck:\n");
        sb.append("#  CustomRole: StandardRole\n");

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
     * Per-check configuration mappings, keyed by check class name. Values are flat maps of
     * configuration key/value strings. Empty when no configuration was provided.
     */
    public Map<String, Map<String, String>> checkConfigs() {
        return checkConfigs;
    }

    // == Builder ==========================================================

    private static class Builder {
        boolean present;
        Optional<List<String>> checks = Optional.empty();
        Map<String, Map<String, String>> checkConfigs = Map.of();

        Builder checks(Optional<List<String>> checks) {
            this.checks = checks;
            return this;
        }

        Builder checkConfigs(Map<String, Map<String, String>> checkConfigs) {
            this.checkConfigs = checkConfigs;
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
                Set<String> knownCheckNames = collectKnownCheckNames();
                warnOnUnknownKeys(path, data, knownCheckNames);
                b.checks(extractChecks(data))
                        .checkConfigs(extractCheckConfigs(data, knownCheckNames));
            }
            return b.build();
        }
    }

    private static Set<String> collectKnownCheckNames() {
        Set<String> names = new HashSet<>();
        for (Supplier<Check> supplier : ProcessingDefaults.allChecks()) {
            names.add(supplier.get().getClass().getSimpleName());
        }
        return names;
    }

    private static void warnOnUnknownKeys(
            Path path, Map<String, Object> data, Set<String> knownCheckNames) {
        for (String key : data.keySet()) {
            if (CHECKS_KEY.equals(key) || knownCheckNames.contains(key)) {
                continue;
            }
            if (LEGACY_KEYS.contains(key)) {
                logger.warn(
                        "Sidecar config {} uses legacy '{}' key, which is no longer supported."
                                + " See docs/sidecar.md for the current schema.",
                        path,
                        key);
            } else {
                logger.warn("Sidecar config {} has unrecognized key '{}'", path, key);
            }
        }
    }

    // == Extraction helpers ===============================================

    private static Optional<List<String>> extractChecks(Map<String, Object> data) {
        if (!data.containsKey(CHECKS_KEY)) {
            return Optional.empty();
        }
        Object value = data.get(CHECKS_KEY);
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

    /**
     * Extracts every top-level key whose name matches a known check, returning {@code checkName ->
     * {configKey -> configValue}}.
     */
    private static Map<String, Map<String, String>> extractCheckConfigs(
            Map<String, Object> data, Set<String> knownCheckNames) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!knownCheckNames.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                result.put(key, Map.of());
                continue;
            }
            Map<String, String> config = new LinkedHashMap<>();
            if (value instanceof List<?> rawList) {
                // List form: synthesize numeric keys ("0", "1", ...) so the value list
                // stays accessible via the same Map<String, String> shape used elsewhere.
                int i = 0;
                for (Object item : rawList) {
                    if (!(item instanceof String s)) {
                        throw new IllegalArgumentException(key + " list items must be strings");
                    }
                    config.put(String.valueOf(i++), s.trim());
                }
            } else if (value instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                    if (!(e.getKey() instanceof String k)) {
                        throw new IllegalArgumentException(key + " keys must be strings");
                    }
                    if (!(e.getValue() instanceof String v)) {
                        throw new IllegalArgumentException(key + " values must be strings");
                    }
                    config.put(k.trim(), v.trim());
                }
            } else {
                throw new IllegalArgumentException(
                        key + " must be a list, a mapping of names to values, or null");
            }
            // Wrap in unmodifiableMap (not Map.copyOf) so the LinkedHashMap's insertion order is
            // preserved — list-form configs like ReorderWebCapturesCheck depend on YAML order.
            result.put(key, Collections.unmodifiableMap(config));
        }
        return Collections.unmodifiableMap(result);
    }
}
