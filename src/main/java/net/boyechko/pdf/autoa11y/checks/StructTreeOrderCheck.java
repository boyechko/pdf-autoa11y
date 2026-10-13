// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Comparator;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.ReadingOrder;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.StructTreeOrderFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects structure tree siblings that are out of reading order, which is taken from where content
 * is painted on the page rather than from MCID order (see {@link ReadingOrder}).
 */
public class StructTreeOrderCheck extends StructTreeCheck {
    private static final Logger logger = LoggerFactory.getLogger(StructTreeOrderCheck.class);

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Structure Tree Order Check";
    }

    @Override
    public String description() {
        return "Structure tree elements should be in reading order";
    }

    @Override
    public void leaveElement(Visit visit) {
        List<PdfStructElem> children = visit.children();
        ReadingOrder order = visit.docCtx().readingOrder();
        if (children.size() >= 2
                && allChildrenLocatable(visit, children, order)
                && !isInOrder(visit.node(), children, order)) {
            IssueFix fix = new StructTreeOrderFix(visit.node());
            issues.add(
                    new Issue(
                            IssueType.STRUCT_TREE_OUT_OF_ORDER,
                            IssueSev.WARNING,
                            visit.locus(),
                            "Children are out of reading order",
                            fix));
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Whether every child paints something findable, without which reordering would guess. */
    private boolean allChildrenLocatable(
            Visit visit, List<PdfStructElem> children, ReadingOrder order) {
        for (PdfStructElem child : children) {
            if (!order.isLocatable(child)) {
                logger.debug(
                        "Not judging the order of #{}: child #{} paints nothing locatable",
                        StructTree.objNum(visit.node()),
                        StructTree.objNum(child));
                return false;
            }
        }
        return true;
    }

    /** Checks whether children already stand in the order their content is read. */
    private boolean isInOrder(
            PdfStructElem parent, List<PdfStructElem> children, ReadingOrder order) {
        Comparator<PdfStructElem> within = order.within(parent);
        for (int i = 1; i < children.size(); i++) {
            if (within.compare(children.get(i), children.get(i - 1)) < 0) {
                return false;
            }
        }
        return true;
    }
}
