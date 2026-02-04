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

    public ProcessingService(Path inputPath, ProcessingListener listener) {
        this(inputPath, null, listener, VerbosityLevel.NORMAL);
    }

    public ProcessingResult remediate() throws Exception {
        Path tempOutputFile = Files.createTempFile("pdf_autoa11y_", ".pdf");

        try (PdfDocument pdfDoc = pdfFactory.openForModification(tempOutputFile)) {
            return remediatePdfDoc(pdfDoc);
        } catch (Exception e) {
            Files.deleteIfExists(tempOutputFile);
            throw e;
        }
    }

    private ProcessingResult remediatePdfDoc(PdfDocument pdfDoc) throws Exception {
        this.context = new DocumentContext(pdfDoc);

        // Phase 1: Detect document issues
        IssueList docIssues = phaseDetectDocumentIssues();

        // Phase 2: Detect tag issues
        IssueList tagIssues = phaseDetectTagIssues();

        // Phase 3: Apply available fixes
        IssueList appliedTagFixes = phaseApplyFixes(tagIssues, "structure tree");
        IssueList appliedDocFixes = phaseApplyFixes(docIssues, "document");

        // Phase 4: Re-detect tag issues
        IssueList remainingTagIssues = tagIssues;
        if (appliedTagFixes.size() > 0) {
            remainingTagIssues = phaseDetectTagIssues();
        }

        // Phase 5: Generate summary
        IssueList totalRemainingIssues = new IssueList();
        totalRemainingIssues.addAll(remainingTagIssues);
        totalRemainingIssues.addAll(docIssues.getRemainingIssues());

        IssueList totalAppliedFixes = new IssueList();
        totalAppliedFixes.addAll(appliedTagFixes);
        totalAppliedFixes.addAll(appliedDocFixes);

        int detected = tagIssues.size() + docIssues.size();
        int remaining = totalRemainingIssues.size();
        int resolved = detected - remaining;
        listener.onSummary(detected, resolved, remaining);

        ProcessingResult result =
                new ProcessingResult(
                        tagIssues,
                        appliedTagFixes,
                        remainingTagIssues,
                        docIssues,
                        appliedDocFixes,
                        totalRemainingIssues,
                        null);
        return result;
    }

    private IssueList phaseDetectDocumentIssues() {
        listener.onPhaseStart("Detecting document-level issues");
        IssueList allDocIssues = new IssueList();

        for (Rule rule : engine.getRules()) {
            IssueList ruleIssues = rule.findIssues(context);
            allDocIssues.addAll(ruleIssues);

            if (ruleIssues.isEmpty()) {
                listener.onSuccess(rule.passedMessage());
            } else {
                reportIssuesGrouped(ruleIssues);
            }
        }
        listener.onInfo("Found " + allDocIssues.size() + " issues");

        return allDocIssues;
    }

    private IssueList phaseDetectTagIssues() {
        listener.onPhaseStart("Detecting structure tree issues");

        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            listener.onError("No structure tree");
        }

        IssueList tagIssues = engine.runVisitors(context);

        if (tagIssues.isEmpty()) {
            listener.onSuccess("No issues found");
        } else {
            listener.onWarning("Found " + tagIssues.size() + " issue(s)");
        }

        return tagIssues;
    }

    private IssueList phaseApplyFixes(IssueList issues, String issueType) {
        listener.onPhaseStart("Applying " + issueType + " fixes");
        if (issues.isEmpty()) {
            listener.onInfo("No " + issueType + " issues to fix");
            return new IssueList();
        }

        IssueList appliedFixes = engine.applyFixes(context, issues);
        reportFixesGrouped(appliedFixes);
        return appliedFixes;
    }

    public IssueList analyze() throws Exception {
        try (PdfDocument pdfDoc = pdfFactory.openForReading()) {
            this.context = new DocumentContext(pdfDoc);

            IssueList documentIssues = phaseDetectDocumentIssues();
            IssueList tagIssues = phaseDetectTagIssues();
            IssueList totalIssues = new IssueList();
            totalIssues.addAll(documentIssues);
            totalIssues.addAll(tagIssues);

            return totalIssues;
        }
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

    /** Given a list of fixes, report them grouped by the fix type. */
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
