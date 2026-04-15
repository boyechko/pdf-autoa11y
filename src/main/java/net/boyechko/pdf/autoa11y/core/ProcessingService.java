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
import java.util.HashMap;
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
        private final Set<String> includeChecks = new HashSet<>();
        private final List<Supplier<Check>> injectedChecks = new ArrayList<>();
        private final Map<String, Supplier<Check>> replacedChecks = new HashMap<>();

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

        public ProcessingServiceBuilder includeChecks(Set<String> checkClassNames) {
            includeChecks.addAll(checkClassNames);
            return this;
        }

        /** Injects a single check supplier directly, bypassing name-based filtering. */
        public ProcessingServiceBuilder injectCheck(Supplier<Check> supplier) {
            injectedChecks.add(Objects.requireNonNull(supplier, "supplier"));
            return this;
        }

        /** Replaces a default check by class name, preserving its position in the pipeline. */
        public ProcessingServiceBuilder replaceCheck(
                String checkClassName, Supplier<Check> replacement) {
            replacedChecks.put(
                    Objects.requireNonNull(checkClassName), Objects.requireNonNull(replacement));
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

        List<Supplier<Check>> filtered =
                selectChecks(
                        ProcessingDefaults.allChecks(),
                        ProcessingDefaults.defaultChecks(),
                        builder.skipChecks,
                        builder.onlyChecks,
                        builder.includeChecks,
                        builder.replacedChecks);
        filtered.addAll(builder.injectedChecks);
        validateCheckPrereqs(filtered);
        this.checks = List.copyOf(filtered);
    }

    // == Filtering and validation =====================================

    /**
     * Selects which checks to run from the full pool of known checks, applying any replaceChecks.
     *
     * <ul>
     *   <li>{@code only}: keep exactly these
     *   <li>{@code include}: add these to the defaults
     *   <li>{@code skip}: remove these from the active set
     *   <li>{@code replaceChecks}: substitute suppliers in-place by class name
     * </ul>
     */
    private static ArrayList<Supplier<Check>> selectChecks(
            List<Supplier<Check>> allChecks,
            List<Supplier<Check>> defaultChecks,
            Set<String> skip,
            Set<String> only,
            Set<String> include,
            Map<String, Supplier<Check>> replaceChecks) {
        java.util.function.Predicate<String> predicate;
        if (!only.isEmpty()) {
            predicate = name -> only.contains(name) && !skip.contains(name);
        } else {
            Set<String> defaultNames = collectNames(defaultChecks);
            predicate =
                    name ->
                            (defaultNames.contains(name) || include.contains(name))
                                    && !skip.contains(name);
        }
        return filterByName(allChecks, predicate, replaceChecks);
    }

    private static ArrayList<Supplier<Check>> filterByName(
            List<Supplier<Check>> checks,
            java.util.function.Predicate<String> predicate,
            Map<String, Supplier<Check>> replaceChecks) {
        ArrayList<Supplier<Check>> result = new ArrayList<>();
        for (Supplier<Check> supplier : checks) {
            String className = supplier.get().getClass().getSimpleName();
            if (predicate.test(className)) {
                Supplier<Check> replacement = replaceChecks.get(className);
                result.add(replacement != null ? replacement : supplier);
            }
        }
        return result;
    }

    private static Set<String> collectNames(List<Supplier<Check>> checks) {
        Set<String> names = new HashSet<>();
        for (Supplier<Check> supplier : checks) {
            names.add(supplier.get().getClass().getSimpleName());
        }
        return names;
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
        boolean isDirty = false;

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

                listener.onCheckStart(check);
                try (PdfDocument doc = PdfCustodian.openTempForModification(current, output)) {
                    DocContext ctx = new DocContext(doc);
                    IssueList issues = runCheck(ctx, check);
                    allIssues.addAll(issues);

                    if (allIssues.hasFatalIssues()) {
                        listener.onSummary(allIssues);
                        cleanupPipelineTempDir();
                        return ProcessingResult.aborted(allIssues);
                    }

                    if (!issues.isEmpty()) {
                        listener.onDetectedSectionStart();
                        reportIssuesGrouped(issues);
                        allFixes.addAll(applyAndReportFixes(ctx, issues));
                        reportRemainingIssues(issues);
                    } else {
                        listener.onSuccess(check.passedMessage());
                    }
                    isDirty |= ctx.isDirty();
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
            cleanupPipelineTempDir();

            return new ProcessingResult(
                    allIssues, allFixes, allIssues.getRemainingIssues(), finalOutput, isDirty);
        } catch (Exception e) {
            cleanupPipelineTempDir();
            throw e;
        }
    }

    public IssueList analyze() throws Exception {
        try (PdfDocument pdfDoc = custodian.openForReading()) {
            DocContext context = new DocContext(pdfDoc);
            IssueList allIssues = new IssueList();

            for (Supplier<Check> supplier : checks) {
                Check check = supplier.get();
                listener.onCheckStart(check);
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
            deleteAllFiles(pipelineDir);
        }
        return pipelineDir;
    }

    /** Cleans up the pipeline directory and temporary files if KEEP_PIPELINE_TEMPS is false. */
    private void cleanupPipelineTempDir() {
        if (!KEEP_PIPELINE_TEMPS) {
            deleteAllFiles(PIPELINE_TEMP_DIR);
            try {
                Files.deleteIfExists(PIPELINE_TEMP_DIR);
            } catch (IOException e) {
                logger.warn("Failed to delete pipeline temp directory", e);
            }
        } else {
            logger.debug("Pipeline temps: {}", PIPELINE_TEMP_DIR);
        }
    }

    private static void deleteAllFiles(Path directory) {
        try (var stream = Files.list(directory)) {
            stream.forEach(
                    path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            throw new RuntimeException("Error deleting pipeline temp file", e);
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private static String sanitizeForFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
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
}
