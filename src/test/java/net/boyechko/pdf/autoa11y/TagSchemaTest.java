package net.boyechko.pdf.autoa11y;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

public class TagSchemaTest {
    @Test
    public void testMinimalSchema() {
        TagSchema schema = TagSchema.minimal();
        assertTrue(schema != null);
        assertTrue(
                schema.getRoles().size() == 4,
                "Expected 4 roles in minimal schema but got " + schema.getRoles().size());
    }

    @Test
    public void testLoadFromResource() {
        var resourceStream = TagSchema.class.getResourceAsStream("/tagschema-PDF-UA1.yaml");
        assertTrue(resourceStream != null);
        TagSchema schema = TagSchema.fromResource("/tagschema-PDF-UA1.yaml");
        assertTrue(schema != null);
        assertTrue(
                schema.getRoles().size() == 39,
                "Expected 39 roles in minimal schema but got " + schema.getRoles().size());
    }

    @Test
    public void testLoadDefault() {
        TagSchema schema = TagSchema.loadDefault();
        assertTrue(schema != null);
        assertTrue(
                schema.getRoles().containsKey("Document"),
                "Default schema should contain 'Document' role");
    }

    @Test
    @Disabled("Disabled to reduce test output clutter")
    public void testDumpDefault() {
        TagSchema schema = TagSchema.loadDefault();
        assertTrue(schema != null);
        Yaml yaml = new Yaml();
        String output = yaml.dump(schema);
        assertTrue(output != null && !output.isEmpty());
        System.out.println("Default TagSchema in YAML format:");
        System.out.println(output);
    }

    @Test
    public void testValidateConsistency() {
        TagSchema schema = TagSchema.loadDefault();
        var warnings = schema.validateConsistency();

        // Print warnings if any
        if (!warnings.isEmpty()) {
            System.out.println("Schema consistency warnings:");
            for (String warning : warnings) {
                System.out.println("  - " + warning);
            }
        }

        // The default schema should be consistent
        assertTrue(
                warnings.isEmpty(),
                "Default schema has " + warnings.size() + " consistency warnings");
    }

    @Test
    public void testValidateConsistencyDetectsAsymmetry() {
        TagSchema schema = new TagSchema();

        // Create asymmetric constraint: LI requires parent L, but L doesn't allow LI
        TagSchema.Rule L = new TagSchema.Rule();
        L.allowed_children = java.util.Set.of("Lbl"); // Missing LI!
        schema.roles.put("L", L);

        TagSchema.Rule LI = new TagSchema.Rule();
        LI.parent_must_be = java.util.Set.of("L");
        schema.roles.put("LI", LI);

        var warnings = schema.validateConsistency();
        assertTrue(warnings.size() > 0, "Should detect asymmetric constraint");
        assertTrue(warnings.get(0).contains("Asymmetric"), "Warning should mention asymmetry");
    }

    @Test
    public void testValidateConsistencyDetectsContradiction() {
        TagSchema schema = new TagSchema();

        // Create contradiction: requires LI but doesn't allow it
        TagSchema.Rule L = new TagSchema.Rule();
        L.required_children = java.util.Set.of("LI");
        L.allowed_children = java.util.Set.of("Lbl"); // Missing LI!
        schema.roles.put("L", L);

        var warnings = schema.validateConsistency();
        assertTrue(warnings.size() > 0, "Should detect contradiction");
        assertTrue(
                warnings.get(0).contains("requires child"),
                "Warning should mention required child contradiction");
    }

    @Test
    public void testValidateConsistencyDetectsMinMaxContradiction() {
        TagSchema schema = new TagSchema();

        // Create contradiction: min > max
        TagSchema.Rule L = new TagSchema.Rule();
        L.min_children = 5;
        L.max_children = 2;
        schema.roles.put("L", L);

        var warnings = schema.validateConsistency();
        assertTrue(warnings.size() > 0, "Should detect min > max contradiction");
        assertTrue(warnings.get(0).contains("min_children"), "Warning should mention min_children");
    }
}
