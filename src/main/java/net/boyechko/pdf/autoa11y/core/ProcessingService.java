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

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.Rule;
import net.boyechko.pdf.autoa11y.validation.RuleEngine;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import net.boyechko.pdf.autoa11y.visitors.VerboseOutputVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orchestrates the processing of a PDF document. */
public class ProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    private final PdfDocumentFactory pdfFactory;
    private final RuleEngine engine;
    private final ProcessingListener listener;

    private DocumentContext context;

    public ProcessingService(
            Path inputPath,
            String password,
            ProcessingListener listener,
            VerbosityLevel verbosity) {
        this.pdfFactory = new PdfDocumentFactory(inputPath, password);
        this.listener = listener;

        List<Rule> rules = ProcessingDefaults.rules();
        List<StructureTreeVisitor> visitors = ProcessingDefaults.visitors();
        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            visitors.add(new VerboseOutputVisitor(listener::onVerboseOutput));
        }

        TagSchema schema = TagSchema.loadDefault();
        this.engine = new RuleEngine(rules, visitors, schema);
    }

    public ProcessingService(Path inputPath, String password, ProcessingListener listener) {
        this(inputPath, password, listener, VerbosityLevel.NORMAL);
    }

    public class NoTagsException extends Exception {
        public NoTagsException(String message) {
            super(message);
        }
    }

    public ProcessingResult process() throws Exception {
        Path tempOutputFile = Files.createTempFile("pdf_autoa11y_", ".pdf");

        try (PdfDocument pdfDoc = pdfFactory.openForModification(tempOutputFile)) {
            this.context = new DocumentContext(pdfDoc);

            analyzeAndRemediate();

            ProcessingResult result = this.context.getProcessingResult();
            return new ProcessingResult(
                    result.originalTagIssues(),
                    result.appliedTagFixes(),
                    result.remainingTagIssues(),
                    result.originalDocumentIssues(),
                    result.appliedDocumentFixes(),
                    result.remainingDocumentIssues(),
                    tempOutputFile);
        } catch (Exception e) {
            Files.deleteIfExists(tempOutputFile);
            throw e;
        }
    }

    public IssueList analyzeOnly() throws Exception {
        try (PdfDocument pdfDoc = pdfFactory.openForReading()) {
            this.context = new DocumentContext(pdfDoc);

            listener.onPhaseStart("Checking document-level compliance");
            IssueList documentIssues = detectAndReportRuleIssues();

            IssueList tagIssues = null;
            boolean hasNoStructTree =
                    documentIssues.stream()
                            .anyMatch(issue -> issue.type() == IssueType.NO_STRUCT_TREE);
            if (hasNoStructTree) {
                listener.onPhaseStart("No tag structure to validate");
                tagIssues = new IssueList();
            } else {
                listener.onPhaseStart("Validating tag structure");
                tagIssues = detectAndReportTagIssues();
            }

            IssueList allIssues = new IssueList();
            allIssues.addAll(documentIssues);
            allIssues.addAll(tagIssues);

            listener.onSummary(allIssues.size(), 0, allIssues.size());
            return allIssues;
        }
    }

    private IssueList analyzeAndRemediate() throws Exception {
        listener.onPhaseStart("Validating tag structure");
        IssueList originalTagIssues = detectAndReportTagIssues();

        listener.onPhaseStart("Applying automatic fixes");
        IssueList appliedTagFixes = applyFixesAndReport(originalTagIssues);
        if (appliedTagFixes.isEmpty()) {
            listener.onInfo("No automatic fixes applied");
        }

        IssueList remainingTagIssues = originalTagIssues;
        if (!appliedTagFixes.isEmpty()) {
            listener.onPhaseStart("Re-validating tag structure");
            remainingTagIssues = detectAndReportTagIssues();
        }

        listener.onPhaseStart("Checking document-level compliance");
        IssueList documentLevelIssues = detectAndReportRuleIssues();

        IssueList appliedDocumentFixes = new IssueList();
        if (!documentLevelIssues.isEmpty()) {
            listener.onPhaseStart("Applying document fixes");
            appliedDocumentFixes = applyFixesAndReport(documentLevelIssues);
        }

        IssueList totalRemainingIssues = new IssueList();
        totalRemainingIssues.addAll(remainingTagIssues);
        totalRemainingIssues.addAll(documentLevelIssues.getRemainingIssues());

        IssueList totalAppliedFixes = new IssueList();
        totalAppliedFixes.addAll(appliedTagFixes);
        totalAppliedFixes.addAll(appliedDocumentFixes);

        int detected = originalTagIssues.size() + documentLevelIssues.size();
        int remaining = totalRemainingIssues.size();
        int resolved = detected - remaining;
        listener.onSummary(detected, resolved, remaining);

        this.context.setProcessingResult(
                new ProcessingResult(
                        originalTagIssues,
                        appliedTagFixes,
                        remainingTagIssues,
                        documentLevelIssues,
                        appliedDocumentFixes,
                        totalRemainingIssues,
                        null));

        return totalAppliedFixes;
    }

    private IssueList detectAndReportTagIssues() throws NoTagsException {
        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            throw new NoTagsException("No accessibility tags found");
        }

        IssueList tagIssues = engine.runVisitors(context);

        if (tagIssues.isEmpty()) {
            listener.onSuccess("No issues found");
        } else {
            listener.onWarning("Found " + tagIssues.size() + " issue(s)");
        }

        return tagIssues;
    }

    private IssueList detectAndReportRuleIssues() {
        IssueList allRuleIssues = new IssueList();

        for (Rule rule : engine.getRules()) {
            IssueList ruleIssues = rule.findIssues(context);
            allRuleIssues.addAll(ruleIssues);

            if (ruleIssues.isEmpty()) {
                listener.onSuccess(rule.passedMessage());
            } else {
                reportIssuesGrouped(ruleIssues);
            }
        }

        return allRuleIssues;
    }

    private void reportIssuesGrouped(IssueList issues) {
        Map<IssueType, List<Issue>> grouped =
                issues.stream().collect(Collectors.groupingBy(Issue::type));

        for (Map.Entry<IssueType, List<Issue>> entry : grouped.entrySet()) {
            List<Issue> groupIssues = entry.getValue();

            if (groupIssues.size() >= 3) {
                listener.onIssueGroup(entry.getKey().groupLabel(), groupIssues);
            } else {
                for (Issue issue : groupIssues) {
                    listener.onWarning(issue.message());
                }
            }
        }
    }

    private IssueList applyFixesAndReport(IssueList issues) {
        if (issues.isEmpty()) {
            return new IssueList();
        }

        IssueList appliedFixes = engine.applyFixes(context, issues);
        reportFixesGrouped(appliedFixes);

        return appliedFixes;
    }

    private void reportFixesGrouped(IssueList appliedFixes) {
        Map<Class<?>, List<Issue>> grouped =
                appliedFixes.stream()
                        .filter(i -> i.isResolved() && i.fix() != null)
                        .collect(Collectors.groupingBy(i -> i.fix().getClass()));

        for (Map.Entry<Class<?>, List<Issue>> entry : grouped.entrySet()) {
            List<Issue> groupFixes = entry.getValue();

            if (groupFixes.size() >= 3) {
                String groupLabel = groupFixes.get(0).fix().groupLabel();
                listener.onFixGroup(groupLabel, groupFixes);
            } else {
                for (Issue issue : groupFixes) {
                    listener.onIssueFixed(issue.resolutionNote());
                }
            }
        }
    }
}
