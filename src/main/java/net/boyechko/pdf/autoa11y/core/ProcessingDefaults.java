// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.boyechko.pdf.autoa11y.checks.*;
import net.boyechko.pdf.autoa11y.validation.Check;

/**
 * Default checks, ordered so that document-level checks run first and structure-tree checks follow
 * in dependency order.
 */
public final class ProcessingDefaults {
    private ProcessingDefaults() {}

    public static List<Supplier<Check>> defaultChecks() {
        return List.of(
                // Document checks
                ImageOnlyDocumentCheck::new,
                StructureTreeExistsCheck::new,
                MissingDocumentCheck::new,
                StructTreeOrderCheck::new,
                UnmarkedLinkCheck::new,
                UnexpectedWidgetCheck::new,
                BadlyMappedLigatureCheck::new,
                LanguageSetCheck::new,
                TabOrderCheck::new,
                TaggedPdfCheck::new,
                PdfUaConformanceCheck::new,
                // Structure tree checks
                OrphanedContentCheck::new,
                NeedlessNestingCheck::new,
                MissingPagePartsCheck::new,
                MistaggedArtifactCheck::new,
                FigureWithTextCheck::new,
                MissingAltTextCheck::new,
                EmptyLinkTagCheck::new,
                InvalidLinkUriCheck::new,
                MistaggedListCheck::new,
                MistaggedHeadingCheck::new,
                EmptyElementCheck::new,
                ScribbledInstructionCheck::new,
                SchemaValidationCheck::new,
                StaleScribbleCheck::new);
    }

    /** Self-contained optional checks that can be activated directly. */
    public static List<Supplier<Check>> optionalChecks() {
        return List.of(
                ClearRoleMapCheck::new,
                () -> new ReplaceRoleMapCheck(Map.of()),
                InlineDestinationsCheck::new,
                MisartifactedTextCheck::new,
                ReorderWebCapturesCheck::new,
                WrapWebCapturesCheck::new);
    }

    /** All known checks: defaults followed by optional. */
    public static List<Supplier<Check>> allChecks() {
        List<Supplier<Check>> all = new ArrayList<>(defaultChecks());
        all.addAll(optionalChecks());
        return all;
    }
}
