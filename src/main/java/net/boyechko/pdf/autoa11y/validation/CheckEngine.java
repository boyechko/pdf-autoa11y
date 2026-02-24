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

/**
 * Orchestrates validation using both structure tree visitors and document-level checks.
 *
 * <p>The engine supports two types of checks:
 *
 * <ul>
 *   <li><b>Visitors</b> ({@link StructTreeChecker}): Run during a single traversal of the structure
 *       tree via {@link StructTreeWalker}. Use for checks that examine tag structure.
 *   <li><b>Rules</b> ({@link Check}): Run independently after the tree walk. Use for document-level
 *       checks (metadata, annotations, pages) that don't need tree traversal.
 * </ul>
 */
public class CheckEngine {
    private static final Logger logger = LoggerFactory.getLogger(CheckEngine.class);

    private final List<Check> checks;
    private final List<Supplier<StructTreeChecker>> visitorSuppliers;
    private final TagSchema schema;

    public CheckEngine(List<Check> checks) {
        this(checks, List.of(), null);
    }

    public CheckEngine(
            List<Check> checks,
            List<Supplier<StructTreeChecker>> visitorSuppliers,
            TagSchema schema) {
        this.checks = List.copyOf(checks);
        this.visitorSuppliers = List.copyOf(visitorSuppliers);
        this.schema = schema;
        validateVisitorPrerequisites();
    }

    private void validateVisitorPrerequisites() {
        Set<Class<? extends StructTreeChecker>> seen = new HashSet<>();
        for (Supplier<StructTreeChecker> supplier : visitorSuppliers) {
            StructTreeChecker visitor = supplier.get();
            for (Class<? extends StructTreeChecker> prereq : visitor.prerequisites()) {
                if (!seen.contains(prereq)) {
                    throw new IllegalArgumentException(
                            visitor.getClass().getSimpleName()
                                    + " requires "
                                    + prereq.getSimpleName()
                                    + " to run first, but it has not been registered"
                                    + " or appears later in the visitor list");
                }
            }
            seen.add(visitor.getClass());
        }
    }

    public List<Check> getRules() {
        return checks;
    }

    public List<Supplier<StructTreeChecker>> getVisitorSuppliers() {
        return visitorSuppliers;
    }

    public IssueList detectIssues(DocContext ctx) {
        IssueList all = new IssueList();

        if (!visitorSuppliers.isEmpty()) {
            IssueList visitorIssues = runVisitors(ctx);
            all.addAll(visitorIssues);
        }

        for (Check r : checks) {
            IssueList found = r.findIssues(ctx);
            all.addAll(found);
        }

        return all;
    }

    public IssueList runVisitors(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            logger.debug("No structure tree found, skipping visitor checks");
            return new IssueList();
        }

        List<StructTreeChecker> visitors = instantiateVisitors();
        StructTreeWalker walker = new StructTreeWalker(schema);
        for (StructTreeChecker visitor : visitors) {
            walker.addVisitor(visitor);
        }

        return walker.walk(root, ctx);
    }

    /** Runs a single visitor in its own tree walk. */
    public IssueList runSingleVisitor(DocContext ctx, Supplier<StructTreeChecker> visitorSupplier) {
        return runVisitor(ctx, visitorSupplier.get());
    }

    /** Runs a pre-instantiated visitor in its own tree walk. */
    public IssueList runVisitor(DocContext ctx, StructTreeChecker visitor) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            logger.debug("No structure tree found, skipping visitor check");
            return new IssueList();
        }

        StructTreeWalker walker = new StructTreeWalker(schema);
        walker.addVisitor(visitor);

        return walker.walk(root, ctx);
    }

    private List<StructTreeChecker> instantiateVisitors() {
        List<StructTreeChecker> visitors = new ArrayList<>(visitorSuppliers.size());
        for (Supplier<StructTreeChecker> visitorSupplier : visitorSuppliers) {
            StructTreeChecker visitor = visitorSupplier.get();
            if (visitor == null) {
                throw new IllegalStateException("Visitor supplier returned null");
            }
            visitors.add(visitor);
        }
        return visitors;
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
                logger.debug(
                        "Skipping fix {}: invalidated by higher priority fix", fx.describe(ctx));
                continue;
            }

            try {
                fx.apply(ctx);
                appliedFixes.add(fx);
                i.markResolved(fx.describe(ctx));
            } catch (Exception ex) {
                i.markFailed(fx.describe(ctx) + " failed: " + ex.getMessage());
                logger.error(
                        "Error applying fix {}: {}",
                        fx.getClass().getSimpleName(),
                        ex.getMessage());
            }
        }

        return new IssueList(issuesToFix.getResolvedIssues());
    }
}
