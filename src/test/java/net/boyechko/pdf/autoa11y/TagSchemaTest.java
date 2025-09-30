package net.boyechko.pdf.autoa11y;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

public class TagSchemaTest {
    @Test
    public void testLoadFromResource() {
        var resourceStream = TagSchema.class.getResourceAsStream("/tagschema-minimal.yaml");
        assert(resourceStream != null);
        TagSchema schema = TagSchema.fromResource("/tagschema-minimal.yaml");
        assert(schema != null);
        assert(schema.getRoles().size() > 0);
    }

    @Test
    public void testLoadDefault() {
        TagSchema schema = TagSchema.loadDefault();
        assert(schema != null);
        assert(schema.getRoles().size() > 0);
    } 

    @Test
    public void testMinimalSchema() {
        TagSchema schema = TagSchema.minimal();
        assert(schema != null);
        assert(schema.getRoles().size() > 0);
    }
}
