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

public class GuiProcessingListener implements ProcessingListener {
    private final OutputFormatter formatter;

    public GuiProcessingListener(OutputFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void onPhaseStart(int step, int total, String phaseName) {
        formatter.printPhase(step, total, phaseName);
    }

    @Override
    public void onSuccess(String message) {
        formatter.printSuccess(message);
    }

    @Override
    public void onWarning(String message) {
        formatter.printWarning(message);
    }

    @Override
    public void onIssueFixed(String resolutionNote) {
        formatter.printSuccess(resolutionNote);
    }

    @Override
    public void onSummary(int detected, int resolved, int remaining) {
        formatter.printSummary(detected, resolved, remaining);
    }

    @Override
    public void onVerboseOutput(String message) {
        formatter.getStream().print(message);
    }
}
