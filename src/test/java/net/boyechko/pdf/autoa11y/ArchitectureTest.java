// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the package layering described in CLAUDE.md. */
@AnalyzeClasses(
        packages = ArchitectureTest.ROOT,
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    static final String ROOT = "net.boyechko.pdf.autoa11y";

    @ArchTest
    static final ArchRule packagesRespectLayering =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .ensureAllClassesAreContainedInArchitecture()
                    .layer("UI")
                    .definedBy(ROOT + ".ui..")
                    .layer("Core")
                    .definedBy(ROOT + ".core..")
                    .layer("Checks")
                    .definedBy(ROOT + ".checks..")
                    .layer("Fixes")
                    .definedBy(ROOT + ".fixes..")
                    .layer("Validation")
                    .definedBy(ROOT + ".validation..")
                    .layer("Issue")
                    .definedBy(ROOT + ".issue..")
                    .layer("Tools")
                    .definedBy(ROOT + ".tools..")
                    .layer("File")
                    .definedBy(ROOT + ".file..")
                    .layer("Document")
                    .definedBy(ROOT + ".document..")
                    .whereLayer("Core")
                    .mayOnlyAccessLayers("Checks", "Validation", "Issue", "File", "Document")
                    .whereLayer("Checks")
                    .mayOnlyAccessLayers("Validation", "Fixes", "Issue", "Document")
                    .whereLayer("Fixes")
                    .mayOnlyAccessLayers("Issue", "Document")
                    .whereLayer("Validation")
                    .mayOnlyAccessLayers("Issue", "Document")
                    .whereLayer("Issue")
                    .mayOnlyAccessLayers("Document")
                    .whereLayer("Tools")
                    .mayOnlyAccessLayers("Document")
                    .whereLayer("File")
                    .mayOnlyAccessLayers("Document")
                    .whereLayer("Document")
                    .mayNotAccessAnyLayer();
}
