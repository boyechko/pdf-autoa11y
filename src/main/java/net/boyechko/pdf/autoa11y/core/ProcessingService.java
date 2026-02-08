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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.Rule;
import net.boyechko.pdf.autoa11y.validation.RuleEngine;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import net.boyechko.pdf.autoa11y.visitors.VerboseOutputVisitor;

/** Orchestrates the processing of a PDF document. */
public class ProcessingService {
    private final PdfCustodian custodian;
    private final RuleEngine ruleEngine;
    private final ProcessingListener listener;
    private final VerbosityLevel verbosityLevel;

    public static class ProcessingServiceBuilder {
        private PdfCustodian custodian;
        private ProcessingListener listener;
        private VerbosityLevel verbosityLevel;

        public ProcessingServiceBuilder withPdfCustodian(PdfCustodian custodian) {
            this.custodian = custodian;
            return this;
        }

        public ProcessingServiceBuilder withListener(ProcessingListener listener) {
            this.listener = listener;
            return this;
        }

        public ProcessingServiceBuilder withVerbosityLevel(VerbosityLevel verbosityLevel) {
            this.verbosityLevel = verbosityLevel;
            return this;
        }

        public ProcessingService build() {
            if (custodian == null) {
                throw new IllegalStateException(
                        "PdfCustodian must be provided via withPdfCustodian(...) before building ProcessingService");
            }
            if (verbosityLevel == null) {
                verbosityLevel = VerbosityLevel.NORMAL;
            }
            return new ProcessingService(this);
        }
    }

    private ProcessingService(ProcessingServiceBuilder builder) {
        this.custodian = builder.custodian;
        this.listener = builder.listener;
        this.verbosityLevel = builder.verbosityLevel;

        List<Rule> rules = ProcessingDefaults.rules();
        List<StructureTreeVisitor> visitors = new ArrayList<>(ProcessingDefaults.visitors());
        if (verbosityLevel.isAtLeast(VerbosityLevel.VERBOSE)) {
            visitors.add(new VerboseOutputVisitor(listener::onVerboseOutput));
        }

        TagSchema schema = TagSchema.loadDefault();
        this.ruleEngine = new RuleEngine(rules, visitors, schema);
    }

    public ProcessingResult remediate() throws Exception {
        Path tempOutputFile = Files.createTempFile("pdf_autoa11y_", ".pdf");

        try (PdfDocument pdfDoc = custodian.openForModification(tempOutputFile)) {
            return remediatePdfDoc(pdfDoc, tempOutputFile);
        } catch (Exception e) {
            Files.deleteIfExists(tempOutputFile);
            throw e;
        }
    }

    public IssueList analyze() throws Exception {
        try (PdfDocument pdfDoc = custodian.openForReading()) {
            DocumentContext context = new DocumentContext(pdfDoc);

            IssueList documentIssues = detectDocumentIssuesPhase(context);
            IssueList tagIssues = detectTagIssuesPhase(context);
            IssueList totalIssues = new IssueList();
            totalIssues.addAll(documentIssues);
            totalIssues.addAll(tagIssues);

            return totalIssues;
        }
    }

    private ProcessingResult remediatePdfDoc(PdfDocument pdfDoc, Path tempOutputFile)
            throws Exception {
        DocumentContext context = new DocumentContext(pdfDoc);

        // Phase 1: Detect document issues
        IssueList docIssues = detectDocumentIssuesPhase(context);

        // Phase 2: Detect tag issues
        IssueList tagIssues = detectTagIssuesPhase(context);

        // Phase 3: Apply available fixes
        IssueList appliedTagFixes = applyFixesPhase(context, tagIssues, "structure tree");
        IssueList appliedDocFixes = applyFixesPhase(context, docIssues, "document");

        // Phase 4: Re-detect tag issues
        IssueList remainingTagIssues = tagIssues;
        if (appliedTagFixes.size() > 0) {
            remainingTagIssues = detectTagIssuesPhase(context);
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
                        docIssues.getRemainingIssues(),
                        tempOutputFile);
        return result;
    }

    private IssueList detectDocumentIssuesPhase(DocumentContext context) {
        listener.onPhaseStart("Detecting document-level issues");
        IssueList allDocIssues = new IssueList();

        for (Rule rule : ruleEngine.getRules()) {
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

    private IssueList detectTagIssuesPhase(DocumentContext context) {
        listener.onPhaseStart("Detecting structure tree issues");

        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            listener.onError("No structure tree");
            return new IssueList();
        }

        IssueList tagIssues = ruleEngine.runVisitors(context);

        if (tagIssues.isEmpty()) {
            listener.onSuccess("No issues found");
        } else {
            listener.onWarning("Found " + tagIssues.size() + " issue(s)");
        }

        return tagIssues;
    }

    private IssueList applyFixesPhase(
            DocumentContext context, IssueList issues, String issuesCategory) {
        listener.onPhaseStart("Applying " + issuesCategory + " fixes");
        if (issues.isEmpty()) {
            listener.onInfo("No " + issuesCategory + " issues to fix");
            return new IssueList();
        }

        IssueList appliedFixes = ruleEngine.applyFixes(context, issues);
        reportFixesGrouped(appliedFixes);
        return appliedFixes;
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
