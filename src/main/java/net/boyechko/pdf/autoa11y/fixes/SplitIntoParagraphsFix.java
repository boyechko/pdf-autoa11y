// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Splits a P whose marked content fuses several paragraphs into one P per paragraph, by way of
 * {@link LineSplitter}: the P keeps the first paragraph, and each further one gets a new P right
 * after it. The paragraph sizes are line counts in reading order; the split refuses, leaving the
 * tree untouched, when they disagree with the lines found in the content stream.
 */
public final class SplitIntoParagraphsFix implements IssueFix {
    private static final String SPLIT_SCRIBBLE = "SPLIT paragraphs";

    private final PdfStructElem element;
    private final List<Integer> paragraphSizes;

    public SplitIntoParagraphsFix(PdfStructElem element, List<Integer> paragraphSizes) {
        this.element = element;
        this.paragraphSizes = List.copyOf(paragraphSizes);
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        List<PdfStructElem> paragraphs =
                new LineSplitter(element, ItemSizes.of(paragraphSizes), new SiblingPlacement())
                        .split(ctx);
        for (PdfStructElem paragraph : paragraphs) {
            StructTree.addToolScribble(paragraph, SPLIT_SCRIBBLE);
        }
    }

    @Override
    public String describe() {
        return "Split " + paragraphSizes.size() + " fused paragraphs into one P each";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), Locus.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "fused paragraphs split into separate P tags";
    }
}
