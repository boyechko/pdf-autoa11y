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

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.rules.*;
import net.boyechko.pdf.autoa11y.validation.Rule;
import net.boyechko.pdf.autoa11y.validation.RuleEngine;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import net.boyechko.pdf.autoa11y.validation.TagValidator;
import net.boyechko.pdf.autoa11y.validation.visitors.FigureWithTextVisitor;
import net.boyechko.pdf.autoa11y.validation.visitors.MistaggedArtifactVisitor;
import net.boyechko.pdf.autoa11y.validation.visitors.NeedlessNestingVisitor;
import net.boyechko.pdf.autoa11y.validation.visitors.VerboseOutputVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    private final Path inputPath;
    private final String password;
    private final ReaderProperties readerProps;
    private final RuleEngine engine;
    private final ProcessingListener listener;
    private final VerbosityLevel verbosity;

    private EncryptionInfo encryptionInfo;
    private DocumentContext context;

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

    private final int DEFAULT_PERMISSIONS =
            EncryptionConstants.ALLOW_PRINTING
                    | EncryptionConstants.ALLOW_FILL_IN
                    | EncryptionConstants.ALLOW_MODIFY_ANNOTATIONS
                    | EncryptionConstants.ALLOW_SCREENREADERS;
    private final int DEFAULT_CRYPTO_MODE =
            EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;

    public ProcessingService(
            Path inputPath,
            String password,
            ProcessingListener listener,
            VerbosityLevel verbosity) {
        this.inputPath = inputPath;
        this.password = password;
        this.readerProps = new ReaderProperties();
        if (password != null) {
            this.readerProps.setPassword(password.getBytes());
        }
        this.listener = listener;
        this.verbosity = verbosity;

        List<Rule> rules =
                List.of(
                        new LanguageSetRule(),
                        new TabOrderRule(),
                        new StructureTreeRule(),
                        new TaggedPdfRule(),
                        new MissingDocumentRule(),
                        new UnmarkedLinkRule(),
                        new EmptyLinkTagRule());

        List<StructureTreeVisitor> visitors = new ArrayList<>();
        visitors.add(new MistaggedArtifactVisitor());
        visitors.add(new NeedlessNestingVisitor());
        visitors.add(new FigureWithTextVisitor());

        if (verbosity.isAtLeast(VerbosityLevel.VERBOSE)) {
            visitors.add(new VerboseOutputVisitor(listener::onVerboseOutput));
        }

        TagSchema schema = TagSchema.loadDefault();
        this.engine = new RuleEngine(rules, visitors, schema);
    }

    public ProcessingService(Path inputPath, String password, ProcessingListener listener) {
        this(inputPath, password, listener, VerbosityLevel.NORMAL);
    }

    public record ProcessingResult(
            IssueList originalTagIssues,
            IssueList appliedTagFixes,
            IssueList remainingTagIssues,
            IssueList documentLevelIssues,
            IssueList appliedDocumentFixes,
            IssueList totalRemainingIssues,
            Path tempOutputFile) {

        public int totalIssuesDetected() {
            return originalTagIssues.size() + documentLevelIssues.size();
        }

        public int totalIssuesResolved() {
            return appliedTagFixes.size() + appliedDocumentFixes.size();
        }

        public int totalIssuesRemaining() {
            return totalRemainingIssues.size();
        }

        public boolean hasTagIssues() {
            return !originalTagIssues.isEmpty();
        }

        public boolean hasDocumentIssues() {
            return !documentLevelIssues.isEmpty();
        }
    }

    public class NoTagsException extends Exception {
        public NoTagsException(String message) {
            super(message);
        }
    }

    public ProcessingResult process() throws Exception {
        validateInputFile();
        Path tempOutputFile = Files.createTempFile("pdf_autoa11y_", ".pdf");

        this.encryptionInfo = analyzeEncryption();

        try (PdfDocument pdfDoc = openForModification(tempOutputFile)) {
            this.context = new DocumentContext(pdfDoc);

            analyzeAndRemediate();

            ProcessingResult result = this.context.getProcessingResult();
            return new ProcessingResult(
                    result.originalTagIssues(),
                    result.appliedTagFixes(),
                    result.remainingTagIssues(),
                    result.documentLevelIssues(),
                    result.appliedDocumentFixes(),
                    result.totalRemainingIssues(),
                    tempOutputFile);
        } catch (Exception e) {
            Files.deleteIfExists(tempOutputFile);
            throw e;
        }
    }

    public IssueList analyzeOnly() throws Exception {
        validateInputFile();
        try (PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
                PdfDocument pdfDoc = new PdfDocument(pdfReader)) {
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

    private void validateInputFile() throws Exception {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("File not found: " + inputPath);
        }
    }

    private EncryptionInfo analyzeEncryption() throws Exception {
        try (PdfReader testReader = new PdfReader(inputPath.toString(), readerProps);
                PdfDocument testDoc = new PdfDocument(testReader)) {
            logger.debug("PDF Encryption Analysis:");
            logger.debug("  Encrypted: " + testReader.isEncrypted());
            logger.debug("  Permissions: " + testReader.getPermissions());
            logger.debug("  Crypto Mode: " + testReader.getCryptoMode());
            return new EncryptionInfo(
                    testReader.getPermissions(),
                    testReader.getCryptoMode(),
                    testReader.isEncrypted());
        }
    }

    private PdfDocument openForModification(Path outputPath) throws Exception {
        PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
        WriterProperties writerProps = new WriterProperties();

        if (encryptionInfo.isEncrypted() && password != null) {
            writerProps.setStandardEncryption(
                    null,
                    password.getBytes(),
                    encryptionInfo.permissions(),
                    encryptionInfo.cryptoMode());
        } else if (password != null) {
            writerProps.setStandardEncryption(
                    null, password.getBytes(), DEFAULT_PERMISSIONS, DEFAULT_CRYPTO_MODE);
        }

        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    private IssueList analyzeAndRemediate() throws Exception {
        listener.onPhaseStart("Validating tag structure");
        IssueList originalTagIssues = detectAndReportTagIssues();

        listener.onPhaseStart("Applying automatic fixes");
        IssueList appliedTagFixes = applyFixesAndReport(originalTagIssues);
        if (appliedTagFixes.isEmpty()) {
            listener.onSuccess("Nothinng to be done");
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

        TagSchema schema = TagSchema.loadDefault();
        TagValidator validator = new TagValidator(schema, getVerboseOutput());
        List<Issue> tagIssues = validator.validate(root);

        IssueList issueList = new IssueList();
        issueList.addAll(tagIssues);

        if (tagIssues.isEmpty()) {
            listener.onSuccess("No issues found");
        } else {
            listener.onWarning("Found " + tagIssues.size() + " issue(s)");
        }

        return issueList;
    }

    private Consumer<String> getVerboseOutput() {
        return verbosity.isAtLeast(VerbosityLevel.VERBOSE) ? listener::onVerboseOutput : null;
    }

    private IssueList detectAndReportRuleIssues() {
        IssueList allRuleIssues = new IssueList();

        for (Rule rule : engine.getRules()) {
            IssueList ruleIssues = rule.findIssues(context);
            allRuleIssues.addAll(ruleIssues);

            if (ruleIssues.isEmpty()) {
                listener.onSuccess(rule.name());
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
