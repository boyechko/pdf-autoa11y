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
package net.boyechko.pdf.autoa11y.ui.cli;

import java.io.PrintStream;
import java.util.List;
import net.boyechko.pdf.autoa11y.core.OutputFormatter;
import net.boyechko.pdf.autoa11y.core.ProcessingListener;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.issues.Issue;

public class CliProcessingListener implements ProcessingListener {
    private final OutputFormatter formatter;

    public CliProcessingListener(PrintStream output, VerbosityLevel verbosity) {
        this.formatter = new OutputFormatter(output, verbosity);
    }

    @Override
    public void onPhaseStart(String phaseName) {
        formatter.printPhase(phaseName);
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

    @Override
    public void onIssueGroup(String groupLabel, List<Issue> issues) {
        formatter.printIssueGroup(groupLabel, issues);
    }

    @Override
    public void onFixGroup(String groupLabel, List<Issue> resolvedIssues) {
        formatter.printFixGroup(groupLabel, resolvedIssues);
    }

    @Override
    public void onSubsection(String header) {
        formatter.printSubsection(header);
    }
}
