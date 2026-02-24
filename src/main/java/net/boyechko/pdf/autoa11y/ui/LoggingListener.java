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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import net.boyechko.pdf.autoa11y.core.ProcessingListener;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import org.slf4j.LoggerFactory;

/** A {@link ProcessingListener} that routes all events through SLF4J. */
public class LoggingListener implements ProcessingListener {

    private static final String CONSOLE_APPENDER_NAME = "AUTOA11Y_CONSOLE";

    private static final org.slf4j.Logger logger =
            LoggerFactory.getLogger("net.boyechko.pdf.autoa11y.processing");

    private final IssueFormatter issueFormatter;

    public LoggingListener() {
        this(new LogIssueFormatter());
    }

    public LoggingListener(IssueFormatter issueFormatter) {
        this.issueFormatter = issueFormatter;
    }

    /** Creates a {@link LoggingListener} and ensures logs are emitted to stdout. */
    public static LoggingListener withConsoleOutput() {
        ensureConsoleAppender();
        return new LoggingListener();
    }

    private static void ensureConsoleAppender() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        if (root.getAppender(CONSOLE_APPENDER_NAME) != null) {
            return;
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%-30logger{0} [%-5level] %msg%n");
        encoder.start();

        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setName(CONSOLE_APPENDER_NAME);
        console.setContext(ctx);
        console.setEncoder(encoder);
        console.start();

        root.addAppender(console);
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
                        + issueFormatter.format(issue.where());
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
        logger.info("FIXED {}: {}", issue.type(), issueFormatter.formatResolution(issue));
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
