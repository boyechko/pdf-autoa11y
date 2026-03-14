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
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates validation checks and fix application. Accepts a unified list of check suppliers and
 * partitions them internally by type ({@code DocumentCheck} vs {@code StructTreeCheck}).
 */
public class CheckEngine {
    private static final Logger logger = LoggerFactory.getLogger(CheckEngine.class);

    private final List<Supplier<Check>> documentChecks;
    private final List<Supplier<Check>> structTreeChecks;
    private final TagSchema schema;

    public CheckEngine(List<Supplier<Check>> checks, TagSchema schema) {
        List<Supplier<Check>> docChecks = new ArrayList<>();
        List<Supplier<Check>> treeChecks = new ArrayList<>();
        for (Supplier<Check> supplier : checks) {
            Check probe = supplier.get();
            if (probe instanceof StructTreeCheck) {
                treeChecks.add(supplier);
            } else {
                docChecks.add(supplier);
            }
        }
        this.documentChecks = List.copyOf(docChecks);
        this.structTreeChecks = List.copyOf(treeChecks);
        this.schema = schema;
        validateCheckPrereqs();
    }

    private void validateCheckPrereqs() {
        Set<Class<? extends Check>> seen = new HashSet<>();
        for (Supplier<Check> supplier : structTreeChecks) {
            Check check = supplier.get();
            for (Class<? extends Check> prereq : check.prerequisites()) {
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

    public List<Supplier<Check>> getDocumentChecks() {
        return documentChecks;
    }

    public List<Supplier<Check>> getStructTreeChecks() {
        return structTreeChecks;
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

    /** Runs all structure tree checks in a single tree walk. */
    public IssueList runStructTreeChecks(DocContext ctx) {
        List<StructTreeCheck> checks = new ArrayList<>(structTreeChecks.size());
        for (Supplier<Check> supplier : structTreeChecks) {
            checks.add((StructTreeCheck) supplier.get());
        }
        return walkStructTree(ctx, checks);
    }

    /** Run a single StructTreeCheck instance. */
    public IssueList runStructTreeCheck(DocContext ctx, StructTreeCheck check) {
        return walkStructTree(ctx, List.of(check));
    }

    public IssueList applyFixes(DocContext ctx, IssueList issuesToFix) {
        List<Map.Entry<Issue, IssueFix>> ordered =
                issuesToFix.stream()
                        .filter(i -> i.fix() != null)
                        .map(i -> Map.entry(i, i.fix()))
                        .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
                        .toList();

        List<IssueFix> appliedFixes = new ArrayList<>();

        for (Map.Entry<Issue, IssueFix> e : ordered) {
            Issue i = e.getKey();
            IssueFix fx = e.getValue();

            boolean isInvalidated =
                    appliedFixes.stream().anyMatch(applied -> applied.invalidates(fx));

            if (isInvalidated) {
                i.markResolved(new IssueMsg("Skipped: resolved by higher priority fix", i.where()));
                continue;
            }

            try {
                fx.apply(ctx);
                appliedFixes.add(fx);
                IssueMsg resolution = fx.describeLocated(ctx);
                i.markResolved(resolution);
            } catch (Exception ex) {
                IssueMsg resolution = fx.describeLocated(ctx);
                i.markFailed(
                        new IssueMsg(
                                resolution.message() + " failed: " + ex.getMessage(),
                                resolution.where()));
            }
        }

        return new IssueList(issuesToFix.getResolvedIssues());
    }
}
