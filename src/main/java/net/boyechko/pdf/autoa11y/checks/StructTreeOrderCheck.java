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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.StructTreeOrderFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Detects structure tree siblings that are out of reading order. Reading order is determined by
 * each element's first MCR appearance: (pageNumber, mcid).
 */
public class StructTreeOrderCheck extends StructTreeCheck {

    /** Position of an element's earliest content in the document's reading flow. */
    public record ReadingPosition(int page, int mcid) implements Comparable<ReadingPosition> {
        static final ReadingPosition MAX =
                new ReadingPosition(Integer.MAX_VALUE, Integer.MAX_VALUE);

        @Override
        public int compareTo(ReadingPosition other) {
            int cmp = Integer.compare(page, other.page);
            return cmp != 0 ? cmp : Integer.compare(mcid, other.mcid);
        }
    }

    private final IssueList issues = new IssueList();
    private final Map<Integer, ReadingPosition> cache = new HashMap<>();
    private int outOfOrderCount;

    @Override
    public String name() {
        return "Structure Tree Order Check";
    }

    @Override
    public String description() {
        return "Structure tree elements should be in reading order";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        List<PdfStructElem> children = ctx.children();
        if (children.size() >= 2 && !isInOrder(children, cache)) {
            outOfOrderCount++;
        }
        // Cache this element's reading position now that all descendants have been visited
        readingPositionOf(ctx.node(), cache);
    }

    @Override
    public void afterTraversal() {
        if (outOfOrderCount > 0) {
            IssueFix fix = new StructTreeOrderFix(cache);
            issues.add(
                    new Issue(
                            IssueType.STRUCT_TREE_OUT_OF_ORDER,
                            IssueSev.WARNING,
                            outOfOrderCount + " elements have children out of reading order",
                            fix));
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Checks whether children are already sorted by reading position. */
    public static boolean isInOrder(
            List<PdfStructElem> children, Map<Integer, ReadingPosition> cache) {
        ReadingPosition prev = null;
        for (PdfStructElem child : children) {
            ReadingPosition key = readingPositionOf(child, cache);
            if (prev != null && key.compareTo(prev) < 0) {
                return false;
            }
            prev = key;
        }
        return true;
    }

    /** Returns the reading position of an element, computing and caching if needed. */
    public static ReadingPosition readingPositionOf(
            PdfStructElem elem, Map<Integer, ReadingPosition> cache) {
        int objNum = StructTree.objNum(elem);
        if (objNum >= 0) {
            ReadingPosition cached = cache.get(objNum);
            if (cached != null) return cached;
        }

        ReadingPosition key =
                StructTree.descendantsOf(elem, PdfMcr.class).stream()
                        .map(mcr -> new ReadingPosition(StructTree.pageOf(mcr), mcr.getMcid()))
                        .min(Comparator.naturalOrder())
                        .orElse(ReadingPosition.MAX);

        if (objNum >= 0) {
            cache.put(objNum, key);
        }
        return key;
    }
}
