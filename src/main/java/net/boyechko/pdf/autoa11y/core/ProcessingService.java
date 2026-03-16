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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.Check;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeWalker;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orchestrates the processing of a PDF document. */
public class ProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    /** Set to true to keep all intermediate pipeline files for debugging. */
    private static final boolean KEEP_PIPELINE_TEMPS = true;

    private static final Path PIPELINE_TEMP_DIR = resolvePipelineTempDir();

    private final PdfCustodian custodian;
    private final ProcessingListener listener;
    private final List<Supplier<Check>> checks;
    private final TagSchema schema;

    public static class ProcessingServiceBuilder {
        private PdfCustodian custodian;
        private ProcessingListener listener;
        private final Set<String> skipChecks = new HashSet<>();
        private final Set<String> onlyChecks = new HashSet<>();
        private final List<Supplier<Check>> includedChecks = new ArrayList<>();
        private final List<Supplier<Check>> injectedChecks = new ArrayList<>();

        public ProcessingServiceBuilder withPdfCustodian(PdfCustodian custodian) {
            this.custodian = custodian;
            return this;
        }

        public ProcessingServiceBuilder withListener(ProcessingListener listener) {
            this.listener = listener;
            return this;
        }

        public ProcessingServiceBuilder skipChecks(Set<String> checkClassNames) {
            skipChecks.addAll(checkClassNames);
            return this;
        }

        public ProcessingServiceBuilder onlyChecks(Set<String> checkClassNames) {
            onlyChecks.addAll(checkClassNames);
            return this;
        }

        /** Injects a single check supplier directly. */
        public ProcessingServiceBuilder injectCheck(Supplier<Check> supplier) {
            injectedChecks.add(Objects.requireNonNull(supplier, "supplier"));
            return this;
        }

        /** Resolves named optional checks from the registry for user-level inclusion. */
        public ProcessingServiceBuilder includeChecks(Set<String> checkNames) {
            if (checkNames.isEmpty()) {
                return this;
            }
            List<Supplier<Check>> optional = ProcessingDefaults.optionalChecks();
            Set<String> remaining = new HashSet<>(checkNames);
            for (Supplier<Check> supplier : optional) {
                String className = supplier.get().getClass().getSimpleName();
                if (remaining.remove(className)) {
                    includedChecks.add(supplier);
                }
            }
            if (!remaining.isEmpty()) {
                List<String> available =
                        optional.stream().map(s -> s.get().getClass().getSimpleName()).toList();
                throw new IllegalArgumentException(
                        "Unknown optional check(s): " + remaining + ". Available: " + available);
            }
            return this;
        }

        public ProcessingService build() {
            if (custodian == null) {
                throw new IllegalStateException(
                        "PdfCustodian must be provided via withPdfCustodian(...) before building"
                                + " ProcessingService");
            }
            return new ProcessingService(this);
        }
    }

    private ProcessingService(ProcessingServiceBuilder builder) {
        this.custodian = builder.custodian;
        this.listener = builder.listener;
        this.schema = TagSchema.loadDefault();

        List<Supplier<Check>> allUserChecks = new ArrayList<>(ProcessingDefaults.defaultChecks());
        allUserChecks.addAll(builder.includedChecks);
        List<Supplier<Check>> filtered =
                filterChecks(allUserChecks, builder.skipChecks, builder.onlyChecks);
        filtered.addAll(builder.injectedChecks);
        validateCheckPrereqs(filtered);
        this.checks = List.copyOf(filtered);
    }

    // == Filtering and validation =====================================

    /**
     * Filters check suppliers based on the skip and includeOnly sets. Probes each supplier once to
     * read its class name; the supplier itself is retained for later fresh instantiation.
     */
    private static ArrayList<Supplier<Check>> filterChecks(
            List<Supplier<Check>> defaults, Set<String> skip, Set<String> includeOnly) {
        if (skip.isEmpty() && includeOnly.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        ArrayList<Supplier<Check>> filtered = new ArrayList<>();
        for (Supplier<Check> supplier : defaults) {
            String className = supplier.get().getClass().getSimpleName();
            if (!includeOnly.isEmpty()) {
                if (includeOnly.contains(className)) {
                    filtered.add(supplier);
                }
            } else if (!skip.contains(className)) {
                filtered.add(supplier);
            }
        }
        return filtered;
    }

    /** Validates that every check's prerequisites appear earlier in the list. */
    private static void validateCheckPrereqs(List<Supplier<Check>> checks) {
        Set<Class<? extends Check>> seen = new HashSet<>();
        for (Supplier<Check> supplier : checks) {
            Check check = supplier.get();
            for (Class<? extends Check> prereq : check.prerequisites()) {
                if (!seen.contains(prereq)) {
                    throw new IllegalArgumentException(
                            check.getClass().getSimpleName()
                                    + " requires "
                                    + prereq.getSimpleName());
                }
            }
            seen.add(check.getClass());
        }
    }

    // == Pipeline =====================================================

    /**
     * Remediates the PDF using a sequential pipeline. Each check runs as its own step, reading the
     * previous step's output file.
     */
    public ProcessingResult remediate() throws Exception {
        Path pipelineDir = initializePipelineTempDir();
        List<Path> tempFiles = new ArrayList<>();

        IssueList allIssues = new IssueList();
        IssueList allFixes = new IssueList();

        try {
            int stepNum = 0;

            // Decrypt original into the first pipeline temp
            Path current = pipelineDir.resolve(String.format("step%02d_decrypt.pdf", stepNum++));
            tempFiles.add(current);
            custodian.decryptToTemp(current).close();

            for (Supplier<Check> supplier : checks) {
                Check check = supplier.get();
                String stepName = sanitizeForFilename(check.name());
                Path output =
                        pipelineDir.resolve(String.format("step%02d_%s.pdf", stepNum++, stepName));
                tempFiles.add(output);

                logger.debug("{} -> {}", check.getClass().getSimpleName(), output.getFileName());
                listener.onPhaseStart(check.name());
                try (PdfDocument doc = PdfCustodian.openTempForModification(current, output)) {
                    DocContext ctx = new DocContext(doc);
                    listener.onDetectedSectionStart();
                    IssueList issues = runCheck(ctx, check);
                    allIssues.addAll(issues);

                    if (allIssues.hasFatalIssues()) {
                        listener.onSummary(allIssues);
                        cleanupPipelineDir(pipelineDir, tempFiles);
                        return ProcessingResult.aborted(allIssues);
                    }

                    if (!issues.isEmpty()) {
                        reportIssuesGrouped(issues);
                        allFixes.addAll(applyAndReportFixes(ctx, issues));
                        reportRemainingIssues(issues);
                    } else {
                        listener.onSuccess(check.passedMessage());
                    }
                }

                if (!KEEP_PIPELINE_TEMPS) {
                    Files.deleteIfExists(current);
                }
                current = output;
            }

            // Finalize: copy the result out of the pipeline directory
            Path finalOutput = pipelineDir.resolve("output.pdf");
            if (custodian.isEncrypted()) {
                custodian.reencrypt(current, finalOutput);
            } else {
                Files.copy(current, finalOutput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            listener.onSummary(allIssues);
            cleanupPipelineDir(pipelineDir, tempFiles);

            return new ProcessingResult(
                    allIssues, allFixes, allIssues.getRemainingIssues(), finalOutput);
        } catch (Exception e) {
            cleanupPipelineDir(pipelineDir, tempFiles);
            throw e;
        }
    }

    public IssueList analyze() throws Exception {
        try (PdfDocument pdfDoc = custodian.openForReading()) {
            DocContext context = new DocContext(pdfDoc);
            IssueList allIssues = new IssueList();

            for (Supplier<Check> supplier : checks) {
                Check check = supplier.get();
                listener.onPhaseStart(check.name());
                IssueList issues = runCheck(context, check);
                allIssues.addAll(issues);

                if (allIssues.hasFatalIssues()) {
                    return allIssues;
                }

                if (!issues.isEmpty()) {
                    reportIssuesGrouped(issues);
                } else {
                    listener.onSuccess(check.passedMessage());
                }
            }

            return allIssues;
        }
    }

    // == Check execution ==============================================

    /** Runs a single check, dispatching by type to the appropriate execution mechanism. */
    private IssueList runCheck(DocContext ctx, Check check) {
        if (check instanceof StructTreeCheck treeCheck) {
            return walkStructTree(ctx, treeCheck);
        }
        return check.findIssues(ctx);
    }

    /** Walks the structure tree with a single check. */
    private IssueList walkStructTree(DocContext ctx, StructTreeCheck check) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            logger.debug("No structure tree found, skipping {}", check.name());
            return new IssueList();
        }

        StructTreeWalker walker = new StructTreeWalker(schema);
        walker.addVisitor(check);
        return walker.walk(root, ctx);
    }

    // == Pipeline helpers =============================================

    private static Path resolvePipelineTempDir() {
        // 1. Explicit JVM flag: -Dautoa11y.pipeline.dir=/my/path
        String sysProp = System.getProperty("autoa11y.pipeline.dir");
        if (sysProp != null) return Path.of(sysProp);

        // 2. Environment variable: export AUTOA11Y_PIPELINE_DIR=/my/path
        String envVar = System.getenv("AUTOA11Y_PIPELINE_DIR");
        if (envVar != null) return Path.of(envVar);

        // 3. Default: /tmp/pipeline/
        return Path.of("/tmp/pdf-autoa11y/pipeline");
    }

    private Path initializePipelineTempDir() throws IOException {
        Path pipelineDir = PIPELINE_TEMP_DIR.resolve(custodian.getInputPath().getFileName());
        if (!Files.exists(pipelineDir)) {
            Files.createDirectories(pipelineDir);
        } else {
            try (var stream = Files.list(pipelineDir)) {
                stream.forEach(
                        path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception e) {
                                throw new RuntimeException("Error deleting pipeline temp file", e);
                            }
                        });
            }
        }
        return pipelineDir;
    }

    // == Reporting helpers ============================================

    private static final int MIN_GROUP_SIZE_FOR_GROUPING = 3;

    /** Applies fixes and reports results. Returns the applied fixes. */
    private IssueList applyAndReportFixes(DocContext ctx, IssueList issues) {
        IssueList applied = issues.applyFixes(ctx);
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

    /** Reports applied fixes, grouped by fix type. */
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

    // == Cleanup ======================================================

    /** Cleans up the pipeline directory and temporary files if KEEP_PIPELINE_TEMPS is false. */
    private static void cleanupPipelineDir(Path pipelineDir, List<Path> tempFiles) {
        if (KEEP_PIPELINE_TEMPS) {
            logger.debug("Pipeline temps kept at: {}", pipelineDir);
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
