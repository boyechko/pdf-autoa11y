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

import net.boyechko.pdf.autoa11y.issues.IssueList;

public class NoOpProcessingListener implements ProcessingListener {
    @Override
    public void onPhaseStart(String phaseName) {}

    @Override
    public void onSuccess(String message) {}

    @Override
    public void onWarning(String message) {}

    @Override
    public void onIssueFixed(String resolutionNote) {}

    @Override
    public void onSummary(IssueList allIssues) {}

    @Override
    public void onSummary(int detected, int resolved, int remaining) {}
}
