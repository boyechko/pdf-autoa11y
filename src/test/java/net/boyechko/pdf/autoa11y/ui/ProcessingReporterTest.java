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
package net.boyechko.pdf.autoa11y.ui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ProcessingReporterTest {
    @Test
    @Tag("visual")
    void rendersMockRunForVisualTuning() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ProcessingReporter reporter =
                new ProcessingReporter(new PrintStream(buffer), VerbosityLevel.NORMAL);

        reporter.onPhaseStart("Validating tag structure");
        reporter.onWarning("Found 1 issue(s)");

        reporter.onPhaseStart("Applying automatic fixes");
        reporter.onSuccess("Nothing to be done");

        reporter.onPhaseStart("Re-validating tag structure");
        reporter.onSuccess("Nothing to be done");

        reporter.onPhaseStart("Checking document-level compliance");
        reporter.onSuccess("Language Set");
        reporter.onSuccess("Tab Order");
        reporter.onSuccess("Tag Structure Present");
        reporter.onSuccess("Tagged PDF");
        reporter.onWarning("Structure tree root has no Document element");
        reporter.onWarning("5 tagged content that should be artifacts");
        reporter.onWarning("31 link annotations not tagged (pages 1-5)");
        reporter.onSuccess("Empty Link Tag Check");
        reporter.onWarning("Found 10 Part/Sect/Art wrapper(s)");
        reporter.onWarning("3 figures containing text");

        reporter.onPhaseStart("Applying document fixes");
        reporter.onSuccess("Flattened 10 unnecessary Part/Sect/Art wrapper(s)");
        reporter.onSuccess("31 Link tags created (pages 1-5)");
        reporter.onSuccess("Set up document structure: created 5 Part(s), moved 48 element(s)");
        reporter.onSuccess("3 Figure roles changed");
        reporter.onSuccess("5 elements converted to artifacts");

        reporter.onSummary(42, 41, 1);
        reporter.onSuccess("Output saved to output/five_acro_autoa11y_pass1.pdf");

        String rendered = normalize(buffer);

        System.out.println("--- Mocked Output Preview ---");
        System.out.print(rendered);
        System.out.println("--- End Preview ---");
    }

    private String normalize(ByteArrayOutputStream buffer) {
        return buffer.toString().replace("\r\n", "\n");
    }
}
