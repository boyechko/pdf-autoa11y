// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.ContentStream;
import net.boyechko.pdf.autoa11y.document.ContentStream.Edit;
import net.boyechko.pdf.autoa11y.document.ContentStream.SplitPlan;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits an element whose single marked-content block lumps together lines of different fonts into
 * one same-role sibling per font run. The block is spliced at every font-change boundary (a line
 * boundary where the effective font resource or size differs from the previous line) so each run
 * becomes its own BDC...EMC block with a fresh MCID; the original element keeps the first run, and
 * each later run moves into a new sibling element of the same role inserted right after it. Lines
 * within a run — including a same-size wrap — stay in a single unsplit block.
 */
public final class SplitMarkedContentFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(SplitMarkedContentFix.class);

    private final PdfStructElem element;

    public SplitMarkedContentFix(PdfStructElem element) {
        this.element = element;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfMcr mcr = onlyMcrKidOf(element);
        PdfPage page = pageOf(ctx, mcr);
        SplitPlan plan = ContentStream.planLineSplit(page, mcr.getMcid());
        List<Integer> splices = plan.fontChangeOffsets();
        if (splices.isEmpty()) {
            // Already a single-font block (e.g. a re-run after a prior split): nothing to do.
            logger.debug("No font changes to split on MCID {}", mcr.getMcid());
            return;
        }

        if (!(element.getParent() instanceof PdfStructElem parent)) {
            throw new IllegalStateException("Element has no structure-element parent");
        }
        int index = StructTree.findKidIndex(parent, element);

        // One same-role sibling per run after the first; the element keeps the first run's MCID.
        List<Integer> newMcids = new ArrayList<>();
        for (int i = 0; i < splices.size(); i++) {
            PdfStructElem sibling = new PdfStructElem(ctx.doc(), element.getRole());
            // Pin /Pg on the MCR's immediate parent so its bare-number MCR resolves its page.
            sibling.getPdfObject().put(PdfName.Pg, page.getPdfObject());
            parent.addKid(index + 1 + i, sibling);
            PdfMcr newMcr = new PdfMcrNumber(page, sibling);
            sibling.addKid(newMcr);
            newMcids.add(newMcr.getMcid());
        }

        List<Edit> edits = ContentStream.blockEditsFor(plan, splices, newMcids::get);
        ContentStream.applyEdits(plan.stream(), edits);

        logger.debug(
                "Split MCID {} on page {} into {} runs (new MCIDs {})",
                mcr.getMcid(),
                ctx.doc().getPageNumber(page),
                splices.size() + 1,
                newMcids);
    }

    /** Returns the element's sole kid, which must be a single marked-content reference. */
    private static PdfMcr onlyMcrKidOf(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids != null && kids.size() == 1 && kids.get(0) instanceof PdfMcr mcr) {
            return mcr;
        }
        throw new IllegalStateException(
                "Splitting by font requires an element with a single marked-content reference");
    }

    /** Resolves the MCR's page, refusing when it cannot be determined. */
    private static PdfPage pageOf(DocContext ctx, PdfMcr mcr) {
        int pageNum = StructTree.pageOf(mcr);
        if (pageNum <= 0) {
            throw new IllegalStateException("Cannot resolve the page of MCID " + mcr.getMcid());
        }
        return ctx.doc().getPage(pageNum);
    }

    @Override
    public String describe() {
        return "Split block mixing fonts into one element per font run";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "font-mixing blocks split into separate elements";
    }
}
