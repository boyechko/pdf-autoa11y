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

    /** Phase 1 — detect everything (audit). */
    public List<Issue> detectAll(ProcessingContext ctx) {
        List<Issue> all = new ArrayList<>();
        for (Rule r : rules) {
            List<Issue> found = r.findIssues(ctx);
            all.addAll(found);
        }
        return all;
    }

    /** Phase 2 — apply fixes in priority order. You decide which issues to apply. */
    public void applyFixes(ProcessingContext ctx, List<Issue> issuesToFix) {
        // sort by IssueFix.priority(), stable for deterministic order
        List<Map.Entry<Issue, IssueFix>> ordered = issuesToFix.stream()
            .map(i -> Map.entry(i, i.fix()))              // compute once
            .filter(e -> e.getValue() != null)
            .sorted(Comparator.comparingInt(e -> e.getValue().priority()))
            .toList();

        // iterate using the cached IssueFix
        for (Map.Entry<Issue, IssueFix> e : ordered) {
            Issue i = e.getKey();
            IssueFix fx = e.getValue();
            try {
                fx.apply(ctx);                  // idempotent by contract
                i.markResolved(fx.describe());
            } catch (Exception ex) {
                i.markFailed(fx.describe() + " failed: " + ex.getMessage());
                logger.error("Error applying fix: " + ex.getMessage());
            }
        }
    }
}