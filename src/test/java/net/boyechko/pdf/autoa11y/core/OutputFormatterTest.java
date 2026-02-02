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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

public class OutputFormatterTest {
    @Test
    void rendersMockRunForVisualTuning() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputFormatter formatter =
                new OutputFormatter(new PrintStream(buffer), VerbosityLevel.NORMAL);
        formatter.printPhase("Validating tag structure");
        formatter.printWarning("Found 1 issue(s)");

        formatter.printPhase("Applying automatic fixes");
        formatter.printSuccess("Nothing to be done");

        formatter.printPhase("Re-validating tag structure");
        formatter.printSuccess("Nothing to be done");

        formatter.printPhase("Checking document-level compliance");
        formatter.printSuccess("Language Set");
        formatter.printSuccess("Tab Order");
        formatter.printSuccess("Tag Structure Present");
        formatter.printSuccess("Tagged PDF");
        formatter.printWarning("Structure tree root has no Document element");
        formatter.printWarning("5 tagged content that should be artifacts");
        formatter.printWarning("31 link annotations not tagged (pages 1-5)");
        formatter.printSuccess("Empty Link Tag Check");
        formatter.printWarning("Found 10 Part/Sect/Art wrapper(s)");
        formatter.printWarning("3 figures containing text");

        formatter.printPhase("Applying document fixes");
        formatter.printSuccess("Flattened 10 unnecessary Part/Sect/Art wrapper(s)");
        formatter.printSuccess("31 Link tags created (pages 1-5)");
        formatter.printSuccess("Set up document structure: created 5 Part(s), moved 48 element(s)");
        formatter.printSuccess("3 Figure roles changed");
        formatter.printSuccess("5 elements converted to artifacts");

        formatter.printSummary(42, 41, 1);
        formatter.printSuccess("Output saved to output/five_acro_autoa11y_pass1.pdf");

        String rendered = normalize(buffer);

        System.out.println("--- Mocked Output Preview ---");
        System.out.print(rendered);
        System.out.println("--- End Preview ---");
    }

    private String normalize(ByteArrayOutputStream buffer) {
        return buffer.toString().replace("\r\n", "\n");
    }
}
