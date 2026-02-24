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

import net.boyechko.pdf.autoa11y.core.ProcessingListener;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A {@link ProcessingListener} that routes all events through SLF4J. */
public class LoggingListener implements ProcessingListener {

    private static final Logger logger =
            LoggerFactory.getLogger("net.boyechko.pdf.autoa11y.processing");

    private final IssueLocFormatter issueLocFormatter;

    public LoggingListener() {
        this(new LogIssueLocFormatter());
    }

    public LoggingListener(IssueLocFormatter issueLocFormatter) {
        this.issueLocFormatter = issueLocFormatter;
    }

    @Override
    public void onPhaseStart(String phaseName) {
        logger.info("PHASE {}", phaseName);
    }

    @Override
    public void onSuccess(String message) {
        logger.info("OK {}", message);
    }

    @Override
    public void onWarning(Issue issue) {
        String message =
                "ISSUE "
                        + issue.type()
                        + ": "
                        + issue.message()
                        + issueLocFormatter.format(issue.where());
        IssueSev severity = issue.severity();
        if (severity == IssueSev.INFO) {
            logger.info("{}", message);
        } else if (severity == IssueSev.WARNING) {
            logger.warn("{}", message);
        } else {
            // ERROR/FATAL
            logger.error("{}", message);
        }
    }

    @Override
    public void onIssueFixed(Issue issue) {
        logger.info("FIXED {}: {}", issue.type(), issue.resolutionNote());
    }

    @Override
    public void onError(String message) {
        logger.error("{}", message);
    }

    @Override
    public void onInfo(String message) {
        logger.info("{}", message);
    }

    @Override
    public void onVerboseOutput(String message) {
        logger.debug("{}", message);
    }

    @Override
    public void onSummary(IssueList allIssues) {
        int detected = allIssues.size();
        int resolved = allIssues.getResolvedIssues().size();
        int remaining = allIssues.getRemainingIssues().size();
        logger.info("SUMMARY detected={} resolved={} remaining={}", detected, resolved, remaining);
    }
}
