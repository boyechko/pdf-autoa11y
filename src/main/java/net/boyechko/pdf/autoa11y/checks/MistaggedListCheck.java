// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects content that reads as a list but is not tagged as one, coordinating four evidence
 * detectors in order of strength:
 *
 * <ol>
 *   <li>{@link BulletCensusDetector}: reads each leaf's lines against the page's bullet glyphs to
 *       find several list items lumped into one tag. Runs on leaves, before any container pass.
 *   <li>{@link BulletRunDetector}: matches a container's whole children to bullet glyphs; runs
 *       become lists, indented runs sublists, and tall elements are drilled into.
 *   <li>{@link IndentRunDetector}: runs of 3+ consecutive P siblings sharing an indented left edge.
 *   <li>{@link LinkParagraphDetector}: elements whose children are all Links, emitted only after
 *       traversal so every stronger pass gets to claim first.
 * </ol>
 *
 * <p>The detectors never emit competing fixes for the same content: each element belongs to the
 * strongest evidence that matched it, enforced through the shared {@link ClaimRegistry} by running
 * detectors strongest-first — leaves before their containers, and within a container bullets before
 * indents, with link paragraphs deferred to the end.
 */
public class MistaggedListCheck extends StructTreeCheck {

    private static final Set<String> CONTAINER_ROLES =
            Set.of("Art", "Part", "Sect", "Div", "Document");

    private final IssueList issues = new IssueList();
    private final ClaimRegistry claims = new ClaimRegistry();
    private final BulletCensusDetector bulletCensus = new BulletCensusDetector(claims, issues);
    private final BulletRunDetector bulletRuns = new BulletRunDetector(claims, issues);
    private final IndentRunDetector indentRuns = new IndentRunDetector(claims, issues);
    private final LinkParagraphDetector linkParagraphs = new LinkParagraphDetector(claims, issues);

    @Override
    public String name() {
        return "Mistagged List Check";
    }

    @Override
    public String description() {
        return "Detects bulleted, indented, or link-only content that should be lists";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        linkParagraphs.collect(ctx);
        return true;
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        // Leaves run first: a leaf claimed as lumped is then off-limits to the passes
        // its container runs, which see only whole elements and would misread it.
        if (ctx.children().isEmpty()) {
            bulletCensus.detect(ctx);
            return;
        }
        if (!CONTAINER_ROLES.contains(ctx.role())) {
            return;
        }

        bulletRuns.detect(ctx);
        indentRuns.detect(ctx);
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        linkParagraphs.emitUnclaimed();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
