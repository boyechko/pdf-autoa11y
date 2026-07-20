// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.StructTreeOrderFix;
import net.boyechko.pdf.autoa11y.issue.*;
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
            IssueFix fix = new StructTreeOrderFix(ctx.node(), cache);
            issues.add(
                    new Issue(
                            IssueType.STRUCT_TREE_OUT_OF_ORDER,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Children are out of reading order",
                            fix));
        }
        // Cache this element's reading position now that all descendants have been visited
        readingPositionOf(ctx.node(), cache);
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
