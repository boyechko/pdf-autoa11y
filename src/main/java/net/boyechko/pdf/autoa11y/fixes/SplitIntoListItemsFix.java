// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.function.Function;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Splits an element whose marked-content blocks lump several one-line list items into one list item
 * per line, by way of {@link LineSplitter}. The element's kids must all be MCRs, possibly spanning
 * pages; the structure tree gains one LI &gt; LBody &gt; P per additional item.
 *
 * <p>When the element already sits inside an LBody &gt; LI &gt; L chain the new items join that
 * list after the original item; a bare element is first wrapped in a new L at its own position. The
 * {@link #inPlace} variant never joins: a P is replaced by the new L, and any other element keeps
 * its place and receives the L as its sole kid, the first line in a new P.
 *
 * <p>The spec is an optional safety catch and grouping control: a plain expected line count makes
 * the fix refuse when the actual total differs (e.g. an item wraps over two lines), and per-item
 * line counts (e.g. {@code 1,1,2}) group consecutive lines into multi-line items — across MCR
 * boundaries when needed.
 */
public final class SplitIntoListItemsFix implements IssueFix {
    private final PdfStructElem element;
    private final ItemSizes itemSizes;
    private final Function<PdfDocument, ListPlacement> placement;

    /** Splits at every detected line, deriving the item count from the content stream. */
    public SplitIntoListItemsFix(PdfStructElem element) {
        this(element, ItemSizes.eachLine(), ListPlacement::joiningEnclosing);
    }

    /** Splits into exactly {@code expectedLines} one-line items, refusing on a count mismatch. */
    public SplitIntoListItemsFix(PdfStructElem element, Integer expectedLines) {
        this(element, ItemSizes.expecting(expectedLines), ListPlacement::joiningEnclosing);
    }

    /** Splits per the instruction's spec, as parsed by {@link ItemSizes#parse}. */
    public SplitIntoListItemsFix(PdfStructElem element, String spec) {
        this(element, ItemSizes.parse(spec), ListPlacement::joiningEnclosing);
    }

    /** Splits per the spec, building the new list where the element is rather than joining one. */
    public static SplitIntoListItemsFix inPlace(PdfStructElem element, String spec) {
        return new SplitIntoListItemsFix(element, ItemSizes.parse(spec), ListPlacement::inPlace);
    }

    private SplitIntoListItemsFix(
            PdfStructElem element,
            ItemSizes itemSizes,
            Function<PdfDocument, ListPlacement> placement) {
        this.element = element;
        this.itemSizes = itemSizes;
        this.placement = placement;
    }

    @Override
    public int priority() {
        return 5; // before the wrap fix (10), which only sees whole elements
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        new LineSplitter(element, itemSizes, placement.apply(ctx.doc())).split(ctx);
    }

    @Override
    public String describe() {
        return "Split lumped block into one list item per line";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), Locus.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "lumped blocks split into list items";
    }
}
