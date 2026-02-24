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
import net.boyechko.pdf.autoa11y.checks.BadlyMappedLigatureCheck;
import net.boyechko.pdf.autoa11y.checks.BulletGlyphVisitor;
import net.boyechko.pdf.autoa11y.checks.EmptyElementVisitor;
import net.boyechko.pdf.autoa11y.checks.EmptyLinkTagVisitor;
import net.boyechko.pdf.autoa11y.checks.FigureWithTextVisitor;
import net.boyechko.pdf.autoa11y.checks.ImageOnlyDocumentCheck;
import net.boyechko.pdf.autoa11y.checks.LanguageSetCheck;
import net.boyechko.pdf.autoa11y.checks.MissingAltTextVisitor;
import net.boyechko.pdf.autoa11y.checks.MissingDocumentCheck;
import net.boyechko.pdf.autoa11y.checks.MistaggedArtifactVisitor;
import net.boyechko.pdf.autoa11y.checks.NeedlessNestingVisitor;
import net.boyechko.pdf.autoa11y.checks.PagePartVisitor;
import net.boyechko.pdf.autoa11y.checks.ParagraphOfLinksVisitor;
import net.boyechko.pdf.autoa11y.checks.PdfUaConformanceCheck;
import net.boyechko.pdf.autoa11y.checks.SchemaValidationVisitor;
import net.boyechko.pdf.autoa11y.checks.StructureTreeExistsCheck;
import net.boyechko.pdf.autoa11y.checks.TabOrderCheck;
import net.boyechko.pdf.autoa11y.checks.TaggedPdfCheck;
import net.boyechko.pdf.autoa11y.checks.UnexpectedWidgetCheck;
import net.boyechko.pdf.autoa11y.checks.UnmarkedLinkCheck;
import net.boyechko.pdf.autoa11y.validation.Rule;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;

public final class ProcessingDefaults {
    private ProcessingDefaults() {}

    public static List<Rule> rules() {
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

    public static List<Supplier<StructureTreeVisitor>> visitorSuppliers() {
        return List.of(
                NeedlessNestingVisitor::new,
                PagePartVisitor::new,
                MistaggedArtifactVisitor::new,
                FigureWithTextVisitor::new,
                MissingAltTextVisitor::new,
                EmptyLinkTagVisitor::new,
                BulletGlyphVisitor::new,
                ParagraphOfLinksVisitor::new,
                EmptyElementVisitor::new,
                SchemaValidationVisitor::new);
    }
}
