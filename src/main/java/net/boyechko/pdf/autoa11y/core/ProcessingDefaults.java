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
import net.boyechko.pdf.autoa11y.rules.LanguageSetRule;
import net.boyechko.pdf.autoa11y.rules.MissingDocumentRule;
import net.boyechko.pdf.autoa11y.rules.StructureTreeRule;
import net.boyechko.pdf.autoa11y.rules.TabOrderRule;
import net.boyechko.pdf.autoa11y.rules.TaggedPdfRule;
import net.boyechko.pdf.autoa11y.rules.UnmarkedLinkRule;
import net.boyechko.pdf.autoa11y.validation.Rule;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.visitors.EmptyLinkTagVisitor;
import net.boyechko.pdf.autoa11y.visitors.FigureWithTextVisitor;
import net.boyechko.pdf.autoa11y.visitors.MistaggedArtifactVisitor;
import net.boyechko.pdf.autoa11y.visitors.NeedlessNestingVisitor;
import net.boyechko.pdf.autoa11y.visitors.SchemaValidationVisitor;
import net.boyechko.pdf.autoa11y.visitors.VerboseOutputVisitor;

public final class ProcessingDefaults {
    private ProcessingDefaults() {}

    public static List<Rule> rules() {
        return List.of(
                new LanguageSetRule(),
                new TabOrderRule(),
                new StructureTreeRule(),
                new TaggedPdfRule(),
                new MissingDocumentRule(),
                new UnmarkedLinkRule());
    }

    public static List<StructureTreeVisitor> visitors(
            ProcessingListener listener, VerbosityLevel verbosity) {
        List<StructureTreeVisitor> visitors = new ArrayList<>();
        visitors.add(new SchemaValidationVisitor());
        visitors.add(new MistaggedArtifactVisitor());
        visitors.add(new NeedlessNestingVisitor());
        visitors.add(new FigureWithTextVisitor());
        visitors.add(new EmptyLinkTagVisitor());

        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            visitors.add(new VerboseOutputVisitor(listener::onVerboseOutput));
        }

        return visitors;
    }
}
