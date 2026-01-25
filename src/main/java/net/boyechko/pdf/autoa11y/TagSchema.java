package net.boyechko.pdf.autoa11y;

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
}
