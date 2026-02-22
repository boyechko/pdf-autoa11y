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
import net.boyechko.pdf.autoa11y.rules.BadlyMappedLigatureRule;
import net.boyechko.pdf.autoa11y.rules.ImageOnlyDocumentRule;
import net.boyechko.pdf.autoa11y.rules.LanguageSetRule;
import net.boyechko.pdf.autoa11y.rules.MissingDocumentRule;
import net.boyechko.pdf.autoa11y.rules.PdfUaConformanceRule;
import net.boyechko.pdf.autoa11y.rules.StructureTreeExistsRule;
import net.boyechko.pdf.autoa11y.rules.TabOrderRule;
import net.boyechko.pdf.autoa11y.rules.TaggedPdfRule;
import net.boyechko.pdf.autoa11y.rules.UnexpectedWidgetRule;
import net.boyechko.pdf.autoa11y.rules.UnmarkedLinkRule;
import net.boyechko.pdf.autoa11y.rules.UnpartitionedDocumentRule;
import net.boyechko.pdf.autoa11y.validation.Rule;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.visitors.BulletGlyphVisitor;
import net.boyechko.pdf.autoa11y.visitors.EmptyElementVisitor;
import net.boyechko.pdf.autoa11y.visitors.EmptyLinkTagVisitor;
import net.boyechko.pdf.autoa11y.visitors.FigureWithTextVisitor;
import net.boyechko.pdf.autoa11y.visitors.MissingAltTextVisitor;
import net.boyechko.pdf.autoa11y.visitors.MistaggedArtifactVisitor;
import net.boyechko.pdf.autoa11y.visitors.NeedlessNestingVisitor;
import net.boyechko.pdf.autoa11y.visitors.ParagraphOfLinksVisitor;
import net.boyechko.pdf.autoa11y.visitors.SchemaValidationVisitor;

public final class ProcessingDefaults {
    private ProcessingDefaults() {}

    public static List<Rule> rules() {
        return List.of(
                new ImageOnlyDocumentRule(),
                new StructureTreeExistsRule(),
                new MissingDocumentRule(),
                new UnpartitionedDocumentRule(),
                new UnmarkedLinkRule(),
                new UnexpectedWidgetRule(),
                new BadlyMappedLigatureRule(),
                new LanguageSetRule(),
                new TabOrderRule(),
                new TaggedPdfRule(),
                new PdfUaConformanceRule());
    }

    public static List<Supplier<StructureTreeVisitor>> visitorSuppliers() {
        return List.of(
                NeedlessNestingVisitor::new,
                MistaggedArtifactVisitor::new,
                FigureWithTextVisitor::new,
                MissingAltTextVisitor::new,
                EmptyLinkTagVisitor::new,
                BulletGlyphVisitor::new,
                ParagraphOfLinksVisitor::new,
                SchemaValidationVisitor::new,
                EmptyElementVisitor::new);
    }
}
