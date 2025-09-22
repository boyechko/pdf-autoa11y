package net.boyechko.pdf.autoa11y;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleEngine {
    private final List<Rule> rules;
    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

    public RuleEngine(List<Rule> rules) { this.rules = List.copyOf(rules); }

    /**
     * Get the list of rules managed by this engine.
     * @return Immutable list of rules
     */
    public List<Rule> getRules() { return rules; }

    /**
     * Detect issues in the document using the defined rules.
     * @param ctx Processing context
     * @return IssueList of detected issues
     */
    public IssueList detectIssues(DocumentContext ctx) {
        IssueList all = new IssueList();
        for (Rule r : rules) {
            IssueList found = r.findIssues(ctx);
            all.addAll(found);
        }
        return all;
    }

    /**
     * Apply fixes to the given issues in order of their IssueFix.priority().
     * If a fix invalidates another fix, the invalidated fix is skipped.
     * @param ctx Processing context
     * @param issuesToFix IssueList of issues to attempt to fix
     * @return IssueList of issues that were successfully fixed
     */
    public IssueList applyFixes(DocumentContext ctx, IssueList issuesToFix) {
        // sort by IssueFix.priority(), stable for deterministic order
        List<Map.Entry<Issue, IssueFix>> ordered = issuesToFix.stream()
            .filter(i -> i.fix() != null)                 // filter nulls first
            .map(i -> Map.entry(i, i.fix()))              // safe to create entry now
            .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
            .toList();

        // Track applied fixes to check for invalidation
        List<IssueFix> appliedFixes = new ArrayList<>();

        // iterate using the cached IssueFix
        for (Map.Entry<Issue, IssueFix> e : ordered) {
            Issue i = e.getKey();
            IssueFix fx = e.getValue();

            // Check if this fix has been invalidated by any previously applied fix
            boolean isInvalidated = appliedFixes.stream().anyMatch(applied -> applied.invalidates(fx));

            if (isInvalidated) {
                i.markResolved("Skipped - resolved by higher priority fix");
                logger.debug("Skipping fix {} - invalidated by higher priority fix", fx.describe());
                continue;
            }

            try {
                fx.apply(ctx);                  // idempotent by contract
                appliedFixes.add(fx);           // Track this fix as applied
                i.markResolved(fx.describe());
            } catch (Exception ex) {
                i.markFailed(fx.describe() + " failed: " + ex.getMessage());
                logger.error("Error applying fix: " + ex.getMessage());
            }
        }

        return new IssueList(issuesToFix.getResolvedIssues());
    }
}
