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
package net.boyechko.pdf.autoa11y.validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public final class TagSchema {
    private static final String DEFAULT_SCHEMA_RESOURCE = "/tagschema-PDF-UA1.yaml";
    private static final Logger logger = LoggerFactory.getLogger(TagSchema.class);

    public Map<String, Rule> roles;

    public static final class Rule {
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

    public TagSchema() {
        this.roles = new HashMap<>();
    }

    public Map<String, Rule> getRoles() {
        return roles;
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

            var yaml = new Yaml(new Constructor(TagSchema.class, new LoaderOptions()));
            TagSchema schema = yaml.load(inputStream);

            logger.debug(
                    "Loaded TagSchema with {} roles from resource {}",
                    schema.roles.size(),
                    resourcePath);
            schema.populateMissingRoles();
            logger.debug(
                    "After populating missing roles, schema has {} roles", schema.roles.size());

            var warnings = schema.validateConsistency();
            if (!warnings.isEmpty()) {
                logger.warn(
                        "Schema loaded from {} has {} consistency warnings:",
                        resourcePath,
                        warnings.size());
                for (String warning : warnings) {
                    logger.warn("  - {}", warning);
                }
            }

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

    public static TagSchema minimal() {
        TagSchema s = new TagSchema();

        Rule L = new Rule();
        L.allowed_children = Set.of("LI");
        L.min_children = 1;
        s.roles.put("L", L);

        Rule LI = new Rule();
        LI.parent_must_be = Set.of("L");
        LI.allowed_children = Set.of("Lbl", "LBody");
        LI.min_children = 1;
        LI.max_children = 2;
        s.roles.put("LI", LI);

        Rule Lbl = new Rule();
        s.roles.put("Lbl", Lbl);

        Rule LBody = new Rule();
        LBody.parent_must_be = Set.of("LI");
        s.roles.put("LBody", LBody);

        s.populateMissingRoles();

        return s;
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
     * @return List of warning messages describing inconsistencies (empty if schema is consistent)
     */
    public java.util.List<String> validateConsistency() {
        java.util.List<String> warnings = new java.util.ArrayList<>();

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
                                    String.format(
                                            "Asymmetric constraint: <%s> requires parent <%s>, but <%s> doesn't list <%s> in allowed_children",
                                            roleName, parentRole, parentRole, roleName));
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
                                    String.format(
                                            "Contradiction: <%s> requires child <%s> but doesn't allow it",
                                            roleName, requiredChild));
                        }
                    }
                }
            }

            // Check 3: min_children vs max_children
            if (rule.min_children != null && rule.max_children != null) {
                if (rule.min_children > rule.max_children) {
                    warnings.add(
                            String.format(
                                    "Contradiction: <%s> has min_children=%d > max_children=%d",
                                    roleName, rule.min_children, rule.max_children));
                }
            }

            // Check 4: max_children vs required_children
            if (rule.max_children != null && rule.required_children != null) {
                if (rule.required_children.size() > rule.max_children) {
                    warnings.add(
                            String.format(
                                    "Contradiction: <%s> requires %d children but max_children=%d",
                                    roleName, rule.required_children.size(), rule.max_children));
                }
            }

            // Check 5: min_children with empty allowed_children
            if (rule.min_children != null && rule.min_children > 0) {
                if (rule.allowed_children != null && rule.allowed_children.isEmpty()) {
                    warnings.add(
                            String.format(
                                    "Contradiction: <%s> has min_children=%d but allowed_children is empty",
                                    roleName, rule.min_children));
                }
            }

            // Check 6: required_children without allowed_children
            if (rule.required_children != null && !rule.required_children.isEmpty()) {
                if (rule.allowed_children == null || rule.allowed_children.isEmpty()) {
                    warnings.add(
                            String.format(
                                    "Suspicious: <%s> has required_children but no allowed_children defined",
                                    roleName));
                }
            }
        }

        return warnings;
    }
}
