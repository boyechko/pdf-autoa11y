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
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Orchestrates validation using both document-level and structure tree checks.
///
/// The engine supports two types of checks:
///
/// - **Checks that work on the document as a whole** (extend [DocumentCheck])
/// - **Checks that walk the structure tree** (extend [StructTreeCheck])
///
public class CheckEngine {
    private static final Logger logger = LoggerFactory.getLogger(CheckEngine.class);

    private final List<Check> documentChecks;
    private final List<Supplier<StructTreeCheck>> structTreeChecks;
    private final TagSchema schema;

    public CheckEngine(List<Check> documentChecks) {
        this(documentChecks, List.of(), null);
    }

    public CheckEngine(
            List<Check> documentChecks,
            List<Supplier<StructTreeCheck>> structTreeChecks,
            TagSchema schema) {
        this.documentChecks = List.copyOf(documentChecks);
        this.structTreeChecks = List.copyOf(structTreeChecks);
        this.schema = schema;
        validateCheckPrereqs();
    }

    private void validateCheckPrereqs() {
        Set<Class<? extends StructTreeCheck>> seen = new HashSet<>();
        for (Supplier<StructTreeCheck> supplier : structTreeChecks) {
            StructTreeCheck check = supplier.get();
            for (Class<? extends StructTreeCheck> prereq : check.prerequisites()) {
                if (!seen.contains(prereq)) {
                    throw new IllegalArgumentException(
                            check.getClass().getSimpleName()
                                    + " requires "
                                    + prereq.getSimpleName()
                                    + " to run first, but it has not been registered"
                                    + " or appears later in the list of checks");
                }
            }
            seen.add(check.getClass());
        }
    }

    public List<Check> getDocumentChecks() {
        return documentChecks;
    }

    public List<Supplier<StructTreeCheck>> getStructTreeChecks() {
        return structTreeChecks;
    }

    public IssueList detectIssues(DocContext ctx) {
        IssueList all = new IssueList();

        if (!structTreeChecks.isEmpty()) {
            IssueList treeCheckIssues = runStructTreeChecks(ctx);
            all.addAll(treeCheckIssues);
        }

        for (Check r : documentChecks) {
            IssueList found = r.findIssues(ctx);
            all.addAll(found);
        }

        return all;
    }

    private IssueList walkStructTree(DocContext ctx, List<StructTreeCheck> checks) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            logger.debug("No structure tree found, skipping structure tree checks");
            return new IssueList();
        }

        StructTreeWalker walker = new StructTreeWalker(schema);
        for (StructTreeCheck check : checks) {
            walker.addVisitor(check);
        }
        return walker.walk(root, ctx);
    }

    public IssueList runStructTreeChecks(DocContext ctx) {
        return walkStructTree(ctx, instantiateStructTreeChecks());
    }

    /// Run a single StructTreeCheck, instantiating it from the supplier.
    public IssueList runStructTreeCheck(DocContext ctx, Supplier<StructTreeCheck> checkSupplier) {
        StructTreeCheck check = checkSupplier.get();
        if (check == null) {
            throw new IllegalStateException("StructTreeCheck supplier returned null");
        }
        return runStructTreeCheck(ctx, check);
    }

    /// Run a single StructTreeCheck instance.
    public IssueList runStructTreeCheck(DocContext ctx, StructTreeCheck check) {
        return walkStructTree(ctx, List.of(check));
    }

    private List<StructTreeCheck> instantiateStructTreeChecks() {
        List<StructTreeCheck> checks = new ArrayList<>(structTreeChecks.size());
        for (Supplier<StructTreeCheck> checkSupplier : structTreeChecks) {
            StructTreeCheck check = checkSupplier.get();
            if (check == null) {
                throw new IllegalStateException("StructTreeCheck supplier returned null");
            }
            checks.add(check);
        }
        return checks;
    }

    public IssueList applyFixes(DocContext ctx, IssueList issuesToFix) {
        List<Map.Entry<Issue, IssueFix>> ordered =
                issuesToFix.stream()
                        .filter(i -> i.fix() != null) // filter nulls first
                        .map(i -> Map.entry(i, i.fix())) // safe to create entry now
                        .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
                        .toList();

        List<IssueFix> appliedFixes = new ArrayList<>();

        for (Map.Entry<Issue, IssueFix> e : ordered) {
            Issue i = e.getKey();
            IssueFix fx = e.getValue();

            boolean isInvalidated =
                    appliedFixes.stream().anyMatch(applied -> applied.invalidates(fx));

            if (isInvalidated) {
                i.markResolved("Skipped: resolved by higher priority fix");
                continue;
            }

            try {
                fx.apply(ctx);
                appliedFixes.add(fx);
                i.markResolved(fx.describe(ctx));
            } catch (Exception ex) {
                i.markFailed(fx.describe(ctx) + " failed: " + ex.getMessage());
            }
        }

        return new IssueList(issuesToFix.getResolvedIssues());
    }
}
