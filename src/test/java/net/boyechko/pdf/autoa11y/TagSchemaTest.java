package net.boyechko.pdf.autoa11y;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
import org.yaml.snakeyaml.Yaml;

public class TagSchemaTest {
    @Test
    public void testMinimalSchema() {
        TagSchema schema = TagSchema.minimal();
        assertTrue(schema != null);
        assertTrue(schema.getRoles().size() == 4, "Expected 4 roles in minimal schema but got " + schema.getRoles().size());
    }

    @Test
    public void testLoadFromResource() {
        var resourceStream = TagSchema.class.getResourceAsStream("/tagschema-minimal.yaml");
        assertTrue(resourceStream != null);
        TagSchema schema = TagSchema.fromResource("/tagschema-minimal.yaml");
        assertTrue(schema != null);
        assertTrue(schema.getRoles().size() == 4, "Expected 3 roles in minimal schema but got " + schema.getRoles().size());
    }

    @Test
    public void testLoadDefault() {
        TagSchema schema = TagSchema.loadDefault();
        assertTrue(schema != null);
        assertTrue(schema.getRoles().containsKey("Document"),
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
}
