// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.document.Link;
import net.boyechko.pdf.autoa11y.fixes.ParagraphOfLinksFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Link-only-paragraph evidence for {@link MistaggedListCheck}: an element whose children are all
 * Links reads as a list of links. The weakest evidence pass — candidates are collected during
 * traversal but emitted only after it ends, so any stronger pass that claims the element in the
 * meantime wins.
 */
final class LinkParagraphDetector {

    private static final Logger logger = LoggerFactory.getLogger(LinkParagraphDetector.class);

    private static final int MIN_LINKS_COUNT = 2;

    private final ClaimRegistry claims;
    private final IssueList issues;
    private final List<Candidate> candidates = new ArrayList<>();

    private record Candidate(PdfStructElem node, List<PdfStructElem> children, IssueLoc loc) {}

    LinkParagraphDetector(ClaimRegistry claims, IssueList issues) {
        this.claims = claims;
        this.issues = issues;
    }

    /** Collects elements whose children are all Links; emitted later unless claimed. */
    void collect(StructTreeContext ctx) {
        if (ctx.children().size() < MIN_LINKS_COUNT) {
            return;
        }

        // Skip if the element has non-struct-elem kids (MCRs/OBJRs) that would
        // be orphaned when we convert Link children to LI > LBody > Link.
        var allKids = ctx.node().getKids();
        if (allKids != null && allKids.size() != ctx.children().size()) {
            return;
        }

        if (!ctx.children().stream().allMatch(c -> c.getRole().equals(PdfName.Link))) {
            return;
        }

        // Links that all point at one destination are one logical link the authoring
        // tool split across lines, not list items. Splitting them into LIs would turn a
        // single entry into a phantom two-item list.
        if (Link.allShareOneDestination(ctx.children())) {
            logger.debug(
                    "Skipping link paragraph {}: all links share one destination",
                    DocValue.ObjNum.of(ctx.node()));
            return;
        }

        IssueLoc loc = StructTreeCheck.locAtElem(ctx);
        candidates.add(new Candidate(ctx.node(), ctx.children(), loc));
    }

    /** Emits link-paragraph issues for candidates no stronger evidence pass claimed. */
    void emitUnclaimed() {
        for (Candidate candidate : candidates) {
            if (claims.isClaimed(candidate.node())) {
                continue;
            }

            issues.add(
                    new Issue(
                            IssueType.PARAGRAPH_OF_LINKS,
                            IssueSev.ERROR,
                            candidate.loc(),
                            "Paragraph contains only links",
                            new ParagraphOfLinksFix(candidate.node(), candidate.children())));
        }
    }
}
