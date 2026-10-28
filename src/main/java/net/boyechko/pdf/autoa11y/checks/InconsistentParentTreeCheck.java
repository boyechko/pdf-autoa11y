// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.InconsistentParentTreeFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/**
 * Detects a page ParentTree that disagrees with the live structure tree: marked-content kids whose
 * reverse index is missing or points at a different element, and ParentTree entries naming elements
 * no longer reachable from the root. Such gaps make content invisible to consumers that rebuild
 * structure from the ParentTree, including iText's page extraction and Acrobat, and are flagged by
 * preflight as "inconsistent ParentTree mapping". A document-level check, since the ParentTree is
 * file-wide bookkeeping: it must cover verified subtrees and see detached elements that no tree
 * walk reaches.
 */
public class InconsistentParentTreeCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Inconsistent ParentTree Check";
    }

    @Override
    public String description() {
        return "Marked content should be reachable from the page ParentTree";
    }

    @Override
    public String passedMessage() {
        return "ParentTree is consistent with the structure tree";
    }

    @Override
    public String failedMessage() {
        return "ParentTree is inconsistent with the structure tree";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) return new IssueList();

        List<PdfStructElem> live = InconsistentParentTreeFix.liveElements(root);
        int detached = InconsistentParentTreeFix.detachedOwners(ctx.doc(), live).size();
        int unregistered = 0;
        for (PdfStructElem element : live) {
            List<IStructureNode> kids = element.getKids();
            if (kids == null) continue;
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfMcr mcr
                        && InconsistentParentTreeFix.needsReregistration(root, element, mcr)) {
                    unregistered++;
                }
            }
        }
        if (detached == 0 && unregistered == 0) return new IssueList();

        return new IssueList(
                new Issue(
                        IssueType.INCONSISTENT_PARENT_TREE,
                        IssueSev.WARNING,
                        unregistered
                                + " marked-content reference(s) missing from ParentTree; "
                                + detached
                                + " detached element(s) still named in it",
                        new InconsistentParentTreeFix()));
    }
}
