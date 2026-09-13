// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static net.boyechko.pdf.autoa11y.document.TagSchema.Warning.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

class TagSchemaTest {
    @Test
    void defaultSchemaClassifiesEveryDeclaredRole() {
        TagSchema schema = TagSchema.loadDefault();

        assertFalse(schema.getRoles().isEmpty());
        schema.getRoles().forEach((role, rule) -> assertFalse(rule.getTypes().isEmpty(), role));
    }

    @Test
    void typeDuplicatesAreIgnored() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          Inline:
                            types: [inline]
                          Heading:
                            types: [block, heading, heading]
                        """);

        assertEquals(Set.of(TagType.INLINE), schema.getRoles().get("Inline").getTypes());
        assertEquals(
                Set.of(TagType.BLOCK, TagType.HEADING),
                schema.getRoles().get("Heading").getTypes());
    }

    @Test
    void missingEmptyAndUnknownRolesHaveNoTypes() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          Untyped:
                            allowed_children: [Generated]
                          Empty:
                            types: []
                        """);

        assertTrue(schema.getRoles().containsKey("Generated"));
        for (String role : Set.of("Untyped", "Empty", "Generated", "Unknown")) {
            for (TagType type : TagType.values()) {
                assertFalse(schema.hasType(role, type), role);
            }
        }
    }

    @Test
    void rejectsUnknownTypeNames() {
        assertThrows(
                YAMLException.class,
                () ->
                        TagSchema.fromYaml(
                                """
                                roles:
                                  Link:
                                    types: [inlien]
                                """));
    }

    @Test
    void rejectsScalarTypes() {
        assertThrows(
                YAMLException.class,
                () ->
                        TagSchema.fromYaml(
                                """
                                roles:
                                  Link:
                                    types: inline
                                """));
    }

    @Test
    void defaultSchemaHasNoConsistencyWarnings() {
        TagSchema schema = TagSchema.loadDefault();

        assertEquals(List.of(), schema.validateConsistency());
    }

    @Test
    void asymmetricParentConstraintIsReported() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          L:
                            allowed_children: [Lbl]
                          LI:
                            parent_must_be: [L]
                        """);

        assertEquals(List.of(ASYMMETRIC_PARENT), kindsOf(schema));
    }

    @Test
    void requiredChildOutsideAllowedChildrenIsReported() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          L:
                            required_children: [LI]
                            allowed_children: [Lbl]
                        """);

        assertEquals(List.of(REQUIRED_CHILD_NOT_ALLOWED), kindsOf(schema));
    }

    @Test
    void minChildrenAboveMaxChildrenIsReported() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          L:
                            min_children: 5
                            max_children: 2
                        """);

        assertEquals(List.of(MIN_EXCEEDS_MAX), kindsOf(schema));
    }

    @Test
    void requiredChildrenExceedingMaxChildrenIsReported() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          L:
                            required_children: [LI, Lbl]
                            allowed_children: [LI, Lbl]
                            max_children: 1
                        """);

        assertEquals(List.of(REQUIRED_EXCEEDS_MAX), kindsOf(schema));
    }

    @Test
    void minChildrenWithEmptyAllowedChildrenIsReported() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          L:
                            min_children: 1
                            allowed_children: []
                        """);

        assertEquals(List.of(MIN_WITH_NO_ALLOWED), kindsOf(schema));
    }

    @Test
    void requiredChildrenWithoutAllowedChildrenIsReported() {
        TagSchema schema =
                TagSchema.fromYaml(
                        """
                        roles:
                          L:
                            required_children: [LI]
                        """);

        assertEquals(List.of(REQUIRED_WITH_NO_ALLOWED), kindsOf(schema));
    }

    private static List<TagSchema.Warning.Kind> kindsOf(TagSchema schema) {
        return schema.validateConsistency().stream().map(TagSchema.Warning::kind).toList();
    }
}
