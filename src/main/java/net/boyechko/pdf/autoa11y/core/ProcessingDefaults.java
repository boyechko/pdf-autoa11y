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

import java.util.List;
import java.util.function.Supplier;
import net.boyechko.pdf.autoa11y.checks.*;
import net.boyechko.pdf.autoa11y.checks.FigureWithTextCheck;
import net.boyechko.pdf.autoa11y.validation.Check;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;

///  Default document- and structure-tree-level checks.
public final class ProcessingDefaults {
    private ProcessingDefaults() {}

    ///  List of default checks that work on the document level.
    public static List<Check> documentChecks() {
        return List.of(
                new ImageOnlyDocumentCheck(),
                new StructureTreeExistsCheck(),
                new MissingDocumentCheck(),
                new UnmarkedLinkCheck(),
                new UnexpectedWidgetCheck(),
                new BadlyMappedLigatureCheck(),
                new LanguageSetCheck(),
                new TabOrderCheck(),
                new TaggedPdfCheck(),
                new PdfUaConformanceCheck());
    }

    ///  List of default checks that work on the structure tree level.
    public static List<Supplier<StructTreeCheck>> structTreeChecks() {
        return List.of(
                NeedlessNestingCheck::new,
                MissingPagePartsCheck::new,
                MistaggedArtifactCheck::new,
                FigureWithTextCheck::new,
                MissingAltTextCheck::new,
                EmptyLinkTagCheck::new,
                MistaggedBulletedListCheck::new,
                ParagraphOfLinksCheck::new,
                EmptyElementCheck::new,
                SchemaValidationCheck::new);
    }
}
