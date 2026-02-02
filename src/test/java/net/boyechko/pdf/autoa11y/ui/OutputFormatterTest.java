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
import org.junit.jupiter.api.Test;

public class OutputFormatterTest {
    @Test
    void rendersMockRunForVisualTuning() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputFormatter formatter =
                new OutputFormatter(new PrintStream(buffer), VerbosityLevel.NORMAL);

        formatter.onPhaseStart("Validating tag structure");
        formatter.onWarning("Found 1 issue(s)");

        formatter.onPhaseStart("Applying automatic fixes");
        formatter.onSuccess("Nothing to be done");

        formatter.onPhaseStart("Re-validating tag structure");
        formatter.onSuccess("Nothing to be done");

        formatter.onPhaseStart("Checking document-level compliance");
        formatter.onSuccess("Language Set");
        formatter.onSuccess("Tab Order");
        formatter.onSuccess("Tag Structure Present");
        formatter.onSuccess("Tagged PDF");
        formatter.onWarning("Structure tree root has no Document element");
        formatter.onWarning("5 tagged content that should be artifacts");
        formatter.onWarning("31 link annotations not tagged (pages 1-5)");
        formatter.onSuccess("Empty Link Tag Check");
        formatter.onWarning("Found 10 Part/Sect/Art wrapper(s)");
        formatter.onWarning("3 figures containing text");

        formatter.onPhaseStart("Applying document fixes");
        formatter.onSuccess("Flattened 10 unnecessary Part/Sect/Art wrapper(s)");
        formatter.onSuccess("31 Link tags created (pages 1-5)");
        formatter.onSuccess("Set up document structure: created 5 Part(s), moved 48 element(s)");
        formatter.onSuccess("3 Figure roles changed");
        formatter.onSuccess("5 elements converted to artifacts");

        formatter.onSummary(42, 41, 1);
        formatter.onSuccess("Output saved to output/five_acro_autoa11y_pass1.pdf");

        String rendered = normalize(buffer);

        System.out.println("--- Mocked Output Preview ---");
        System.out.print(rendered);
        System.out.println("--- End Preview ---");
    }

    private String normalize(ByteArrayOutputStream buffer) {
        return buffer.toString().replace("\r\n", "\n");
    }
}
