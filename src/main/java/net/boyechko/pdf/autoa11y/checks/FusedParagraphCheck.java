// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.Content.TextLine;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Geometry;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.TagSchema;
import net.boyechko.pdf.autoa11y.document.TagType;
import net.boyechko.pdf.autoa11y.fixes.SplitIntoParagraphsFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Detects several paragraphs fused into one {@code <P>} tag — typically left-aligned paragraphs
 * with neither an indent nor a blank line between them, which a tagger read as one block. A screen
 * reader announces them as a single paragraph.
 *
 * <p>Text set greedily (each line taking as many words as fit) leaves a telltale at every paragraph
 * end: a line that stops short even though the next line's first word would have fit after it. A
 * line ends a paragraph when it ends a sentence and the gap from its right edge to the block's
 * widest line holds the next line's first word plus a space. Since a wrapped line by construction
 * had no room for that word, the test does not fire mid-paragraph; measuring against the widest
 * line, which can only underestimate the true measure, keeps it on the safe side.
 *
 * <p>Only a P whose kids are marked content or inline elements such as {@code Link} or {@code Span}
 * is considered, and only when at least one line wraps — proof the block is flowing text rather
 * than, say, a column of short sentences. A P in a list item's LBody is left to {@link
 * MistaggedListCheck}, since lines lumped there are usually items. P tags carrying their own {@code
 * /Alt} or {@code /ActualText} are left alone, since splitting would leave one text equivalent
 * standing for several paragraphs.
 */
public class FusedParagraphCheck extends StructTreeCheck {

    /** Characters a paragraph-ending line may end with: sentence and clause ends, closing marks. */
    private static final String PARAGRAPH_END = ".!?:;)\"'”’";

    /** How far (pt) the next word must clear the gap, so rounding cannot fake a fit. */
    private static final float FIT_TOLERANCE = 1.0f;

    private static final TagSchema schema = TagSchema.loadDefault();

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Fused Paragraph Check";
    }

    @Override
    public String description() {
        return "Several paragraphs fused into one P tag should each have their own P";
    }

    @Override
    public boolean enterElement(Visit visit) {
        PdfStructElem node = visit.node();
        if (!"P".equals(StructTree.mappedRole(node))
                || hasTextEquivalent(node)
                || isListItemBody(node.getParent())
                || !holdsOnlyTextAndInlines(visit)) {
            return true;
        }
        List<PdfMcr> mcrs = StructTree.descendantsOf(node, PdfMcr.class);
        if (mcrs.isEmpty()) {
            return true;
        }

        List<Integer> sizes = paragraphSizes(linesOf(mcrs, visit.docCtx()));
        if (sizes.size() >= 2) {
            issues.add(
                    new Issue(
                            IssueType.PARAGRAPH_FUSED,
                            IssueSev.WARNING,
                            visit.locus(),
                            sizes.size() + " paragraphs fused into one P",
                            new SplitIntoParagraphsFix(node, sizes)));
        }
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** True for an LBody, whose lumped lines are list items for MistaggedListCheck to split. */
    private static boolean isListItemBody(IStructureNode parent) {
        return parent instanceof PdfStructElem elem && "LBody".equals(StructTree.mappedRole(elem));
    }

    /** True if every kid is marked content or an inline element, whose lines are the P's own. */
    private static boolean holdsOnlyTextAndInlines(Visit visit) {
        PdfStructElem node = visit.node();
        int mcrCount = StructTree.childrenOf(node, PdfMcr.class).size();
        return mcrCount + visit.children().size() == StructTree.kidsOf(node).size()
                && visit.childRoles().stream()
                        .allMatch(role -> schema.hasType(role, TagType.INLINE));
    }

    private static boolean hasTextEquivalent(PdfStructElem elem) {
        return elem.getPdfObject().get(PdfName.Alt) != null
                || elem.getPdfObject().get(PdfName.ActualText) != null;
    }

    /**
     * Returns the element's text lines in reading order, joining an MCR's first line to the
     * previous MCR's last when they share a line, as the split counts them.
     */
    private static List<TextLine> linesOf(List<PdfMcr> mcrs, DocContext ctx) {
        List<TextLine> lines = new ArrayList<>();
        PdfMcr previous = null;
        for (PdfMcr mcr : mcrs) {
            List<TextLine> mcrLines = ctx.pages().textLines(mcr);
            int first = 0;
            if (!lines.isEmpty()
                    && !mcrLines.isEmpty()
                    && Content.continuesLineOf(ctx, previous, mcr)) {
                TextLine head = lines.removeLast();
                TextLine tail = mcrLines.get(0);
                lines.add(
                        new TextLine(
                                Geometry.union(head.bounds(), tail.bounds()),
                                head.firstWordWidth(),
                                head.spaceWidth(),
                                tail.lastChar()));
                first = 1;
            }
            lines.addAll(mcrLines.subList(first, mcrLines.size()));
            previous = mcr;
        }
        return lines;
    }

    /**
     * Returns the line count of each paragraph in reading order, or a single size when the lines
     * read as one paragraph or no line wraps.
     */
    private static List<Integer> paragraphSizes(List<TextLine> lines) {
        float widestRight =
                (float) lines.stream().mapToDouble(l -> l.bounds().getRight()).max().orElse(0);
        List<Integer> sizes = new ArrayList<>();
        boolean wraps = false;
        int size = 0;
        for (int i = 0; i < lines.size(); i++) {
            size++;
            if (i == lines.size() - 1) {
                break;
            }
            if (endsParagraph(lines.get(i), lines.get(i + 1), widestRight)) {
                sizes.add(size);
                size = 0;
            } else {
                wraps = true;
            }
        }
        sizes.add(size);
        return wraps ? sizes : List.of(lines.size());
    }

    /** True if the line ends a sentence and the next line's first word would have fit after it. */
    private static boolean endsParagraph(TextLine line, TextLine next, float widestRight) {
        float gap = widestRight - line.bounds().getRight();
        float needed = next.spaceWidth() + next.firstWordWidth() + FIT_TOLERANCE;
        return PARAGRAPH_END.indexOf(line.lastChar()) >= 0 && gap >= needed;
    }
}
