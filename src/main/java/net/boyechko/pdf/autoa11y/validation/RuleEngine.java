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
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates validation using both structure tree visitors and document-level rules.
 *
 * <p>The engine supports two types of checks:
 *
 * <ul>
 *   <li><b>Visitors</b> ({@link StructureTreeVisitor}): Run during a single traversal of the
 *       structure tree via {@link StructureTreeWalker}. Use for checks that examine tag structure.
 *   <li><b>Rules</b> ({@link Rule}): Run independently after the tree walk. Use for document-level
 *       checks (metadata, annotations, pages) that don't need tree traversal.
 * </ul>
 */
public class RuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;
    private final List<Supplier<StructureTreeVisitor>> visitorSuppliers;
    private final TagSchema schema;

    public RuleEngine(List<Rule> rules) {
        this(rules, List.of(), null);
    }

    public RuleEngine(
            List<Rule> rules,
            List<Supplier<StructureTreeVisitor>> visitorSuppliers,
            TagSchema schema) {
        this.rules = List.copyOf(rules);
        this.visitorSuppliers = List.copyOf(visitorSuppliers);
        this.schema = schema;
        validateVisitorPrerequisites();
    }

    private void validateVisitorPrerequisites() {
        Set<Class<? extends StructureTreeVisitor>> seen = new HashSet<>();
        for (Supplier<StructureTreeVisitor> supplier : visitorSuppliers) {
            StructureTreeVisitor visitor = supplier.get();
            for (Class<? extends StructureTreeVisitor> prereq : visitor.prerequisites()) {
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

    public List<Rule> getRules() {
        return rules;
    }

    public List<Supplier<StructureTreeVisitor>> getVisitorSuppliers() {
        return visitorSuppliers;
    }

    public IssueList detectIssues(DocumentContext ctx) {
        IssueList all = new IssueList();

        if (!visitorSuppliers.isEmpty()) {
            IssueList visitorIssues = runVisitors(ctx);
            all.addAll(visitorIssues);
        }

        for (Rule r : rules) {
            IssueList found = r.findIssues(ctx);
            all.addAll(found);
        }

        return all;
    }

    public IssueList runVisitors(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            logger.debug("No structure tree found, skipping visitor checks");
            return new IssueList();
        }

        List<StructureTreeVisitor> visitors = instantiateVisitors();
        StructureTreeWalker walker = new StructureTreeWalker(schema);
        for (StructureTreeVisitor visitor : visitors) {
            walker.addVisitor(visitor);
        }

        return walker.walk(root, ctx);
    }

    /** Runs a single visitor in its own tree walk. */
    public IssueList runSingleVisitor(
            DocumentContext ctx, Supplier<StructureTreeVisitor> visitorSupplier) {
        return runVisitor(ctx, visitorSupplier.get());
    }

    /** Runs a pre-instantiated visitor in its own tree walk. */
    public IssueList runVisitor(DocumentContext ctx, StructureTreeVisitor visitor) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null || root.getKids() == null) {
            logger.debug("No structure tree found, skipping visitor check");
            return new IssueList();
        }

        StructureTreeWalker walker = new StructureTreeWalker(schema);
        walker.addVisitor(visitor);

        return walker.walk(root, ctx);
    }

    private List<StructureTreeVisitor> instantiateVisitors() {
        List<StructureTreeVisitor> visitors = new ArrayList<>(visitorSuppliers.size());
        for (Supplier<StructureTreeVisitor> visitorSupplier : visitorSuppliers) {
            StructureTreeVisitor visitor = visitorSupplier.get();
            if (visitor == null) {
                throw new IllegalStateException("Visitor supplier returned null");
            }
            visitors.add(visitor);
        }
        return visitors;
    }

    public IssueList applyFixes(DocumentContext ctx, IssueList issuesToFix) {
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
