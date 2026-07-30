// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.ContentStream;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructTree;
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
 * Detects an element whose single marked-content block lumps together lines of different font sizes
 * — where each size run reads as a distinct piece (e.g. a heading followed by its subtitle at
 * another size). Such a block is flagged for splitting into one same-role element per run. Only
 * roles that legally repeat in sequence are considered ({@link #SPLITTABLE_ROLES}), so splitting a
 * table cell or list body — where a same-role sibling would corrupt the grid or list — is never
 * proposed. Because sizes are compared only at line boundaries, a paragraph wrapped at one size is
 * never flagged, and a same-size inline font change (e.g. a bolded word) cannot trigger a split.
 */
public class BadlyMarkedContentCheck extends StructTreeCheck {
    private static final Logger logger = LoggerFactory.getLogger(BadlyMarkedContentCheck.class);

    /**
     * Roles whose block may be split into same-role siblings. Limited to paragraphs and headings,
     * where a sequence of siblings is structurally valid; table cells (TD/TH) and list-structure
     * roles (LBody/LI/Lbl) are excluded because a sibling there breaks the parent's contract.
     */
    private static final Set<String> SPLITTABLE_ROLES =
            Set.of("P", "H1", "H2", "H3", "H4", "H5", "H6");

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Badly Marked Content Check";
    }

    @Override
    public String description() {
        return "Marked-content blocks lumping differently-sized lines";
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        if (!SPLITTABLE_ROLES.contains(StructTree.mappedRole(node))) {
            return true;
        }
        PdfMcr mcr = onlyMcrKid(node);
        if (mcr == null) {
            return true;
        }
        int pageNum = ctx.getPageNumber();
        if (pageNum <= 0) {
            return true;
        }

        SplitPlan plan = planSplit(ctx.doc().getPage(pageNum), mcr.getMcid());
        if (plan == null || plan.sizeChangeOffsets().isEmpty() || !splittable(plan)) {
            return true;
        }

        String text = Format.truncate(Content.getTextForElement(node, ctx.docCtx(), pageNum));
        IssueFix fix = new SplitMarkedContentFix(node);
        issues.add(
                new Issue(
                        IssueType.MIXED_FONT_MARKED_CONTENT,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "Marked content mixing font sizes: \"" + text + "\"",
                        fix));
        return true;
    }

    /**
     * Returns the element's sole kid when it is a single marked-content reference with real
     * content, else null. An OBJR (object reference to an annotation) is a {@link PdfMcr} too but
     * marks no content and reports its MCID as -1, so it is excluded.
     */
    private static PdfMcr onlyMcrKid(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids != null
                && kids.size() == 1
                && kids.get(0) instanceof PdfMcr mcr
                && mcr.getMcid() >= 0) {
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

    /** True when the block's size-change splices can be realized without an illegal splice. */
    private static boolean splittable(SplitPlan plan) {
        try {
            ContentStream.blockEditsFor(plan, plan.sizeChangeOffsets(), i -> 0);
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
