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
package net.boyechko.pdf.autoa11y.core;

import java.util.ArrayList;
import java.util.List;
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
                NeedlessNestingCheck::new,
                MissingPagePartsCheck::new,
                MistaggedArtifactCheck::new,
                FigureWithTextCheck::new,
                MissingAltTextCheck::new,
                EmptyLinkTagCheck::new,
                MistaggedBulletedListCheck::new,
                ParagraphOfLinksCheck::new,
                EmptyElementCheck::new,
                SchemaValidationCheck::new,
                StaleAnnotationCheck::new);
    }

    /** Self-contained optional checks that can be activated directly. */
    public static List<Supplier<Check>> optionalChecks() {
        return List.of(ClearRoleMapCheck::new);
    }

    /** All known checks: defaults followed by optional. */
    public static List<Supplier<Check>> allChecks() {
        List<Supplier<Check>> all = new ArrayList<>(defaultChecks());
        all.addAll(optionalChecks());
        return all;
    }
}
