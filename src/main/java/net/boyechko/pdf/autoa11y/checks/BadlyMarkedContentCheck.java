// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.ContentStream;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.fixes.SplitMarkedContentFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects an element whose single marked-content block lumps together lines of different fonts —
 * differing in /Font resource or in effective (matrix-scaled) size — where each font run reads as a
 * distinct piece (e.g. a heading followed by its subtitle at another size). Such a block is flagged
 * for splitting into one same-role element per run. Because the comparison is made only at line
 * boundaries, a paragraph wrapped across lines at one size is never flagged, and an inline font
 * change within a line cannot trigger a split.
 */
public class BadlyMarkedContentCheck extends StructTreeCheck {
    private static final Logger logger = LoggerFactory.getLogger(BadlyMarkedContentCheck.class);

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Badly Marked Content Check";
    }

    @Override
    public String description() {
        return "Marked-content blocks lumping differently-fonted lines";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        PdfMcr mcr = onlyMcrKid(node);
        if (mcr == null) {
            return true;
        }
        int pageNum = ctx.getPageNumber();
        if (pageNum <= 0) {
            return true;
        }

        SplitPlan plan = planSplit(ctx.doc().getPage(pageNum), mcr.getMcid());
        if (plan == null || plan.fontChangeOffsets().isEmpty() || !splittable(plan)) {
            return true;
        }

        String text = Format.truncate(Content.getTextForElement(node, ctx.docCtx(), pageNum));
        IssueFix fix = new SplitMarkedContentFix(node);
        issues.add(
                new Issue(
                        IssueType.MIXED_FONT_MARKED_CONTENT,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "Marked content mixing fonts: \"" + text + "\"",
                        fix));
        return true;
    }

    /** Returns the element's sole kid when it is a single marked-content reference, else null. */
    private static PdfMcr onlyMcrKid(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids != null && kids.size() == 1 && kids.get(0) instanceof PdfMcr mcr) {
            return mcr;
        }
        return null;
    }

    /** Plans the block's line splits, returning null when the block cannot be located or read. */
    private static SplitPlan planSplit(PdfPage page, int mcid) {
        try {
            return ContentStream.planLineSplit(page, mcid);
        } catch (Exception e) {
            logger.debug("Cannot plan split for MCID {}: {}", mcid, e.getMessage());
            return null;
        }
    }

    /** True when the block's font-change splices can be realized without an illegal splice. */
    private static boolean splittable(SplitPlan plan) {
        try {
            ContentStream.blockEditsFor(plan, plan.fontChangeOffsets(), i -> 0);
            return true;
        } catch (RuntimeException e) {
            logger.debug(
                    "Block for tag {} cannot be split cleanly: {}", plan.tag(), e.getMessage());
            return false;
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
