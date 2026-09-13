// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

/** Role classifications and structural constraints loaded from YAML. */
public final class TagSchema {
    private static final String DEFAULT_SCHEMA_RESOURCE = "/tagschema-PDF-UA1.yaml";
    private static final Logger logger = LoggerFactory.getLogger(TagSchema.class);

    public Map<String, Rule> roles;

    public static final class Rule {
        public Set<TagType> types = Set.of();

        /**
         * parent_must_be (Optional): If specified, this element can only appear under the listed
         * parent roles. If null or empty, the element can appear under any parent that lists it in
         * allowed_children.
         *
         * <p>Note: This is typically redundant with parent allowed_children constraints, but can be
         * useful for creating stricter custom schemas, expressing child-centric constraints more
         * clearly, or validating that parents properly declare their children.
         */
        public Set<String> parent_must_be;

        public Set<String> allowed_children;
        public Set<String> required_children;
        public Integer min_children;
        public Integer max_children;
        public String child_pattern;

        public Set<TagType> getTypes() {
            return types;
        }

        public Set<String> getParentMustBe() {
            return parent_must_be;
        }

        public Set<String> getAllowedChildren() {
            return allowed_children;
        }

        public Set<String> getRequiredChildren() {
            return required_children;
        }

        public Integer getMinChildren() {
            return min_children;
        }

        public Integer getMaxChildren() {
            return max_children;
        }

        public String getChildPattern() {
            return child_pattern;
        }
    }

    /** A schema inconsistency, named by kind so callers need not parse the message. */
    public record Warning(Kind kind, String message) {
        public enum Kind {
            ASYMMETRIC_PARENT,
            REQUIRED_CHILD_NOT_ALLOWED,
            MIN_EXCEEDS_MAX,
            REQUIRED_EXCEEDS_MAX,
            MIN_WITH_NO_ALLOWED,
            REQUIRED_WITH_NO_ALLOWED
        }

        private static Warning of(Kind kind, String format, Object... args) {
            return new Warning(kind, String.format(format, args));
        }
    }

    public TagSchema() {
        this.roles = new HashMap<>();
    }

    public Map<String, Rule> getRoles() {
        return roles;
    }

    /** Whether the role explicitly declares this type; unknown and untyped roles return false. */
    public boolean hasType(String role, TagType type) {
        Rule rule = roles.get(role);
        return rule != null && rule.types.contains(type);
    }

    /**
     * Load TagSchema from classpath resource (e.g., from src/main/resources/)
     *
     * @param resourcePath Path starting with "/" for absolute resource path
     */
    public static TagSchema fromResource(String resourcePath) {
        try (var inputStream = TagSchema.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }

            TagSchema schema =
                    fromYaml(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            logger.debug(
                    "Loaded TagSchema with {} roles from {}", schema.roles.size(), resourcePath);

            return schema;
        } catch (Exception e) {
            logger.error(
                    "Failed to load TagSchema from resource {}: {}", resourcePath, e.getMessage());
            throw new RuntimeException(
                    "Failed to load schema from resource " + resourcePath + ": " + e.getMessage(),
                    e);
        }
    }

    /** Load default schema from standard location */
    public static TagSchema loadDefault() {
        return fromResource(DEFAULT_SCHEMA_RESOURCE);
    }

    /** Loads a schema from YAML text using the same rules as classpath resources. */
    public static TagSchema fromYaml(String source) {
        var options = new LoaderOptions();
        options.setEnumCaseSensitive(false);
        var constructor = new Constructor(TagSchema.class, options);
        constructor.addTypeDescription(
                new TypeDescription(Rule.class) {
                    @Override
                    public boolean setupPropertyType(String key, Node valueNode) {
                        if ("types".equals(key) && !(valueNode instanceof SequenceNode)) {
                            throw new YAMLException("Role types must be a list");
                        }
                        return super.setupPropertyType(key, valueNode);
                    }
                });
        var yaml = new Yaml(constructor);
        TagSchema schema = yaml.load(source);
        schema.populateMissingRoles();

        var warnings = schema.validateConsistency();
        if (!warnings.isEmpty()) {
            logger.warn("Tag schema has {} consistency warnings:", warnings.size());
            for (Warning warning : warnings) {
                logger.warn("  - {}", warning.message());
            }
        }
        return schema;
    }

    private void populateMissingRoles() {
        Set<String> existingRoles = roles.keySet();
        Set<String> referencedRoles = new HashSet<>();

        // Collect all roles that are referenced in constraints
        for (Rule rule : roles.values()) {
            if (rule.parent_must_be != null) {
                referencedRoles.addAll(rule.parent_must_be);
            }
            if (rule.allowed_children != null) {
                referencedRoles.addAll(rule.allowed_children);
            }
            if (rule.required_children != null) {
                referencedRoles.addAll(rule.required_children);
            }
        }

        for (String role : referencedRoles) {
            if (!existingRoles.contains(role)) {
                logger.debug(
                        "Role '{}' is referenced but not defined in schema; adding with no constraints",
                        role);
                roles.put(role, new Rule());
            }
        }
    }

    /**
     * Validates the internal consistency of the schema and returns a list of warnings. This checks
     * for: - Asymmetric parent_must_be constraints (child requires parent, but parent doesn't allow
     * child) - Contradictory child count constraints - Required children not in allowed children
     *
     * @return List of warnings describing inconsistencies (empty if schema is consistent)
     */
    public List<Warning> validateConsistency() {
        List<Warning> warnings = new ArrayList<>();

        for (Map.Entry<String, Rule> entry : roles.entrySet()) {
            String roleName = entry.getKey();
            Rule rule = entry.getValue();

            // Check 1: Asymmetric parent_must_be constraints
            if (rule.parent_must_be != null) {
                for (String parentRole : rule.parent_must_be) {
                    Rule parentRule = roles.get(parentRole);
                    if (parentRule != null && parentRule.allowed_children != null) {
                        if (!parentRule.allowed_children.isEmpty()
                                && !parentRule.allowed_children.contains(roleName)) {
                            warnings.add(
                                    Warning.of(
                                            Warning.Kind.ASYMMETRIC_PARENT,
                                            "Asymmetric constraint: <%s> requires parent <%s>, but <%s> doesn't list <%s> in allowed_children",
                                            roleName,
                                            parentRole,
                                            parentRole,
                                            roleName));
                        }
                    }
                }
            }

            // Check 2: Required children must be in allowed children
            if (rule.required_children != null && rule.allowed_children != null) {
                if (!rule.allowed_children.isEmpty()) {
                    for (String requiredChild : rule.required_children) {
                        if (!rule.allowed_children.contains(requiredChild)) {
                            warnings.add(
                                    Warning.of(
                                            Warning.Kind.REQUIRED_CHILD_NOT_ALLOWED,
                                            "Contradiction: <%s> requires child <%s> but doesn't allow it",
                                            roleName,
                                            requiredChild));
                        }
                    }
                }
            }

            // Check 3: min_children vs max_children
            if (rule.min_children != null && rule.max_children != null) {
                if (rule.min_children > rule.max_children) {
                    warnings.add(
                            Warning.of(
                                    Warning.Kind.MIN_EXCEEDS_MAX,
                                    "Contradiction: <%s> has min_children=%d > max_children=%d",
                                    roleName,
                                    rule.min_children,
                                    rule.max_children));
                }
            }

            // Check 4: max_children vs required_children
            if (rule.max_children != null && rule.required_children != null) {
                if (rule.required_children.size() > rule.max_children) {
                    warnings.add(
                            Warning.of(
                                    Warning.Kind.REQUIRED_EXCEEDS_MAX,
                                    "Contradiction: <%s> requires %d children but max_children=%d",
                                    roleName,
                                    rule.required_children.size(),
                                    rule.max_children));
                }
            }

            // Check 5: min_children with empty allowed_children
            if (rule.min_children != null && rule.min_children > 0) {
                if (rule.allowed_children != null && rule.allowed_children.isEmpty()) {
                    warnings.add(
                            Warning.of(
                                    Warning.Kind.MIN_WITH_NO_ALLOWED,
                                    "Contradiction: <%s> has min_children=%d but allowed_children is empty",
                                    roleName,
                                    rule.min_children));
                }
            }

            // Check 6: required_children without allowed_children
            if (rule.required_children != null && !rule.required_children.isEmpty()) {
                if (rule.allowed_children == null || rule.allowed_children.isEmpty()) {
                    warnings.add(
                            Warning.of(
                                    Warning.Kind.REQUIRED_WITH_NO_ALLOWED,
                                    "Suspicious: <%s> has required_children but no allowed_children defined",
                                    roleName));
                }
            }
        }

        return warnings;
    }
}
