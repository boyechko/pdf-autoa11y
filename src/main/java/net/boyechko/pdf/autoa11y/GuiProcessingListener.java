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
package net.boyechko.pdf.autoa11y;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class GuiProcessingListener implements ProcessingListener {
    private final JTextArea outputArea;

    public GuiProcessingListener(JTextArea outputArea) {
        this.outputArea = outputArea;
    }

    @Override
    public void onPhaseStart(int step, int total, String phaseName) {
        appendText(String.format("\n[%d/%d] %s...\n", step, total, phaseName));
    }

    @Override
    public void onSuccess(String message) {
        appendText("  ✓ " + message + "\n");
    }

    @Override
    public void onWarning(String message) {
        appendText("\n  ⚠ " + message + "\n");
    }

    @Override
    public void onIssueFixed(String resolutionNote) {
        appendText("  ✓ " + resolutionNote + "\n");
    }

    @Override
    public void onSummary(int detected, int resolved, int remaining) {
        appendText("\n" + "─".repeat(50) + "\n");
        appendText("Summary\n");
        appendText("─".repeat(50) + "\n");
        if (detected == 0 && resolved == 0) {
            appendText("✓ Document is already compliant\n");
        } else {
            appendText("  Issues detected: " + detected + "\n");
            appendText("  ✓ Resolved: " + resolved + "\n");
            if (remaining > 0) {
                appendText("  ⚠ Manual review needed: " + remaining + "\n");
            }
        }
    }

    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> outputArea.append(text));
    }
}
