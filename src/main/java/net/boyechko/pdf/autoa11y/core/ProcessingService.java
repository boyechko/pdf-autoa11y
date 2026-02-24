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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.Check;
import net.boyechko.pdf.autoa11y.validation.CheckEngine;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import net.boyechko.pdf.autoa11y.visitors.VerboseOutputVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orchestrates the processing of a PDF document. */
public class ProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    /** Set to true to keep all intermediate pipeline files for debugging. */
    private static final boolean KEEP_PIPELINE_TEMPS = true;

    private static final Path PIPELINE_TEMP_DIR = resolvePipelineTempDir();

    private final PdfCustodian custodian;
    private final CheckEngine checkEngine;
    private final ProcessingListener listener;
    private final List<Supplier<StructureTreeVisitor>> visitorSuppliers;

    public static class ProcessingServiceBuilder {
        private PdfCustodian custodian;
        private ProcessingListener listener;
        private boolean printStructureTree;
        private final Set<String> skipVisitors = new HashSet<>();
        private final Set<String> includeOnlyVisitors = new HashSet<>();

        public ProcessingServiceBuilder withPdfCustodian(PdfCustodian custodian) {
            this.custodian = custodian;
            return this;
        }

        public ProcessingServiceBuilder withListener(ProcessingListener listener) {
            this.listener = listener;
            return this;
        }

        public ProcessingServiceBuilder withPrintStructureTree(boolean printStructureTree) {
            this.printStructureTree = printStructureTree;
            return this;
        }

        public ProcessingServiceBuilder skipVisitors(Set<String> visitorClassNames) {
            skipVisitors.addAll(visitorClassNames);
            return this;
        }

        public ProcessingServiceBuilder includeOnlyVisitors(Set<String> visitorClassNames) {
            includeOnlyVisitors.addAll(visitorClassNames);
            return this;
        }

        public ProcessingService build() {
            if (custodian == null) {
                throw new IllegalStateException(
                        "PdfCustodian must be provided via withPdfCustodian(...) before building ProcessingService");
            }
            return new ProcessingService(this);
        }
    }

    private ProcessingService(ProcessingServiceBuilder builder) {
        this.custodian = builder.custodian;
        this.listener = builder.listener;

        List<Check> checks = ProcessingDefaults.rules();
        this.visitorSuppliers =
                filterVisitors(
                        ProcessingDefaults.visitorSuppliers(),
                        builder.skipVisitors,
                        builder.includeOnlyVisitors);
        if (builder.printStructureTree) {
            visitorSuppliers.add(() -> new VerboseOutputVisitor(listener::onVerboseOutput));
        }

        TagSchema schema = TagSchema.loadDefault();
        this.checkEngine = new CheckEngine(checks, visitorSuppliers, schema);
    }

    /** Filters the list of visitor suppliers based on the skip and includeOnly sets. */
    private static ArrayList<Supplier<StructureTreeVisitor>> filterVisitors(
            List<Supplier<StructureTreeVisitor>> defaults,
            Set<String> skip,
            Set<String> includeOnly) {
        if (skip.isEmpty() && includeOnly.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        ArrayList<Supplier<StructureTreeVisitor>> filtered = new ArrayList<>();
        Set<String> removedNames = new HashSet<>();
        for (Supplier<StructureTreeVisitor> supplier : defaults) {
            String className = supplier.get().getClass().getSimpleName();
            if (!includeOnly.isEmpty()) {
                if (includeOnly.contains(className)) {
                    filtered.add(supplier);
                } else {
                    removedNames.add(className);
                }
            } else if (!skip.contains(className)) {
                filtered.add(supplier);
            } else {
                removedNames.add(className);
            }
        }
        validateNoMissingPrerequisites(filtered, removedNames);
        return filtered;
    }

    private static void validateNoMissingPrerequisites(
            List<Supplier<StructureTreeVisitor>> visitors, Set<String> removedNames) {
        for (Supplier<StructureTreeVisitor> supplier : visitors) {
            StructureTreeVisitor visitor = supplier.get();
            for (Class<? extends StructureTreeVisitor> prereq : visitor.prerequisites()) {
                String prereqName = prereq.getSimpleName();
                if (removedNames.contains(prereqName)) {
                    throw new IllegalArgumentException(
                            visitor.getClass().getSimpleName()
                                    + " requires "
                                    + prereqName
                                    + ", which was excluded."
                                    + " To skip both, use: --skip-visitors "
                                    + prereqName
                                    + ","
                                    + visitor.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Remediates the PDF using a sequential pipeline. Each rule/visitor runs as its own step,
     * reading the previous step's output file.
     */
    public ProcessingResult remediate() throws Exception {
        Path pipelineDir = PIPELINE_TEMP_DIR.resolve(custodian.getInputPath().getFileName());
        if (!Files.exists(pipelineDir)) {
            Files.createDirectories(pipelineDir);
        } else {
            Files.list(pipelineDir)
                    .forEach(
                            path -> {
                                try {
                                    Files.delete(path);
                                } catch (Exception e) {
                                    logger.error("Error deleting pipeline temp file", e);
                                    throw new RuntimeException(
                                            "Error deleting pipeline temp file", e);
                                }
                            });
        }
        List<Path> tempFiles = new ArrayList<>();

        IssueList allDocIssues = new IssueList();
        IssueList allDocFixes = new IssueList();
        IssueList allTagIssues = new IssueList();
        IssueList allTagFixes = new IssueList();

        try {
            int stepNum = 0;

            // Step 0: Document-level rules (decrypt original → first temp)
            Path current =
                    pipelineDir.resolve(String.format("step%02d_document-rules.pdf", stepNum++));
            tempFiles.add(current);
            listener.onPhaseStart("Document rules");
            try (PdfDocument doc = custodian.decryptToTemp(current)) {
                DocumentContext ctx = new DocumentContext(doc);
                listener.onDetectedSectionStart();
                IssueList docIssues = runDocumentRules(ctx);
                allDocIssues.addAll(docIssues);

                if (allDocIssues.hasFatalIssues()) {
                    listener.onSummary(allDocIssues);
                    cleanupPipelineDir(pipelineDir, tempFiles);
                    return ProcessingResult.aborted(allDocIssues);
                }

                if (!docIssues.isEmpty()) {
                    IssueList docFixes = applyFixes(ctx, docIssues);
                    allDocFixes.addAll(docFixes);
                    reportRemainingIssues(docIssues);
                }
            }

            // Steps 1..N: Each visitor in its own pipeline step
            for (Supplier<StructureTreeVisitor> supplier : visitorSuppliers) {
                StructureTreeVisitor visitor = supplier.get();
                String stepName = sanitizeForFilename(visitor.name());
                Path output =
                        pipelineDir.resolve(String.format("step%02d_%s.pdf", stepNum++, stepName));
                tempFiles.add(output);

                listener.onPhaseStart(visitor.name());
                try (PdfDocument doc = PdfCustodian.openTempForModification(current, output)) {
                    DocumentContext ctx = new DocumentContext(doc);
                    listener.onDetectedSectionStart();
                    IssueList issues = checkEngine.runVisitor(ctx, visitor);
                    allTagIssues.addAll(issues);

                    if (!issues.isEmpty()) {
                        reportIssuesGrouped(issues);
                        IssueList fixes = applyFixes(ctx, issues);
                        allTagFixes.addAll(fixes);
                        reportRemainingIssues(issues);
                    } else {
                        listener.onSuccess("No issues found");
                    }
                }

                if (!KEEP_PIPELINE_TEMPS) {
                    Files.deleteIfExists(current);
                }
                current = output;
            }

            // Finalize: copy result out of pipeline directory
            Path finalOutput = pipelineDir.resolve("output.pdf");
            if (custodian.isEncrypted()) {
                custodian.reencrypt(current, finalOutput);
            } else {
                Files.copy(current, finalOutput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Summary
            IssueList allIssues = new IssueList();
            allIssues.addAll(allTagIssues);
            allIssues.addAll(allDocIssues);
            listener.onSummary(allIssues);

            // Cleanup pipeline directory (final output is safely outside it)
            cleanupPipelineDir(pipelineDir, tempFiles);

            return new ProcessingResult(
                    allTagIssues,
                    allTagFixes,
                    allIssues.getRemainingIssues(),
                    allDocIssues,
                    allDocFixes,
                    allIssues.getRemainingIssues(),
                    finalOutput);
        } catch (Exception e) {
            cleanupPipelineDir(pipelineDir, tempFiles);
            throw e;
        }
    }

    public IssueList analyze() throws Exception {
        try (PdfDocument pdfDoc = custodian.openForReading()) {
            DocumentContext context = new DocumentContext(pdfDoc);

            IssueList documentIssues = detectDocumentIssuesForAnalysis(context);
            if (documentIssues.hasFatalIssues()) {
                return documentIssues;
            }

            IssueList tagIssues = detectTagIssuesForAnalysis(context);
            IssueList totalIssues = new IssueList();
            totalIssues.addAll(documentIssues);
            totalIssues.addAll(tagIssues);

            return totalIssues;
        }
    }

    // == Pipeline helpers =============================================

    private static Path resolvePipelineTempDir() {
        // 1. Explicit JVM flag:  -Dautoa11y.pipeline.dir=/my/path
        String sysProp = System.getProperty("autoa11y.pipeline.dir");
        if (sysProp != null) return Path.of(sysProp);

        // 2. Environment variable: export AUTOA11Y_PIPELINE_DIR=/my/path
        String envVar = System.getenv("AUTOA11Y_PIPELINE_DIR");
        if (envVar != null) return Path.of(envVar);

        // 3. Default: /tmp/pipeline/
        return Path.of("/tmp/pdf-autoa11y/pipeline");
    }

    /** Runs all document rules, reporting per-rule pass/fail. Stops early on FATAL issues. */
    private IssueList runDocumentRules(DocumentContext ctx) {
        IssueList allDocIssues = new IssueList();
        for (Check check : checkEngine.getRules()) {
            IssueList ruleIssues = check.findIssues(ctx);
            allDocIssues.addAll(ruleIssues);
            if (ruleIssues.isEmpty()) {
                listener.onSuccess(check.passedMessage());
            } else {
                reportIssuesGrouped(ruleIssues);
            }
            if (allDocIssues.hasFatalIssues()) {
                break;
            }
        }
        return allDocIssues;
    }

    /** Applies fixes and reports results. Returns the applied fixes. */
    private IssueList applyFixes(DocumentContext ctx, IssueList issues) {
        if (issues.isEmpty()) {
            return new IssueList();
        }
        IssueList applied = checkEngine.applyFixes(ctx, issues);
        if (!applied.isEmpty()) {
            listener.onFixesSectionStart();
        }
        reportFixesGrouped(applied);
        return applied;
    }

    private void reportRemainingIssues(IssueList issues) {
        IssueList remainingIssues = issues.getRemainingIssues();
        if (remainingIssues.isEmpty()) {
            return;
        }
        listener.onManualReviewSectionStart();
        reportIssuesGrouped(remainingIssues);
    }

    // == Analysis helpers =============================================

    private IssueList detectDocumentIssuesForAnalysis(DocumentContext context) {
        listener.onPhaseStart("Detecting document-level issues");
        IssueList docIssues = runDocumentRules(context);
        listener.onInfo("Found " + docIssues.size() + " issues");
        return docIssues;
    }

    private IssueList detectTagIssuesForAnalysis(DocumentContext context) {
        listener.onPhaseStart("Detecting structure tree issues");

        PdfStructTreeRoot root = context.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            listener.onError("No structure tree");
            return new IssueList();
        }

        IssueList tagIssues = checkEngine.runVisitors(context);

        if (tagIssues.isEmpty()) {
            listener.onSuccess("No issues found");
        } else {
            reportIssuesGrouped(tagIssues);
            listener.onInfo("Found " + tagIssues.size() + " issue(s)");
        }

        return tagIssues;
    }

    // == Reporting helpers ============================================

    private static final int MIN_GROUP_SIZE_FOR_GROUPING = 3;

    private void reportIssuesGrouped(IssueList issues) {
        Map<IssueType, List<Issue>> grouped =
                issues.stream().collect(Collectors.groupingBy(Issue::type));

        for (Map.Entry<IssueType, List<Issue>> entry : grouped.entrySet()) {
            List<Issue> groupIssues = entry.getValue();

            if (groupIssues.size() >= MIN_GROUP_SIZE_FOR_GROUPING) {
                listener.onIssueGroup(entry.getKey().groupLabel(), groupIssues);
            } else {
                for (Issue issue : groupIssues) {
                    listener.onWarning(issue);
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

            if (groupFixes.size() >= MIN_GROUP_SIZE_FOR_GROUPING) {
                String groupLabel = groupFixes.get(0).fix().groupLabel();
                listener.onFixGroup(groupLabel, groupFixes);
            } else {
                for (Issue issue : groupFixes) {
                    listener.onIssueFixed(issue);
                }
            }
        }
    }

    /**
     * Cleans up the pipeline directory and temporary files if the {@link#KEEP_PIPELINE_TEMPS} flag
     * is false.
     */
    private static void cleanupPipelineDir(Path pipelineDir, List<Path> tempFiles) {
        if (KEEP_PIPELINE_TEMPS) {
            logger.info("Pipeline temps kept at: {}", pipelineDir);
            return;
        }
        for (Path temp : tempFiles) {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
        try {
            Files.deleteIfExists(pipelineDir);
        } catch (Exception ignored) {
        }
    }

    private static String sanitizeForFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
