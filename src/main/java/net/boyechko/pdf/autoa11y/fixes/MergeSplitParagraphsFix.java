// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges P tags that are fragments of one paragraph into a single {@code <P>}: the later tags'
 * content moves into the first tag, keeping its own page, and the emptied tags are removed.
 *
 * <p>Applies only while the P tags are still immediate siblings, so a merge invalidated by an
 * earlier fix is skipped rather than forced. The surviving P is tool-stamped {@code MERGE
 * paragraphs} so the sites are findable in {@code --dump-tree}.
 */
public final class MergeSplitParagraphsFix implements IssueFix {

    private static final Logger logger = LoggerFactory.getLogger(MergeSplitParagraphsFix.class);

    private static final String MERGED_SCRIBBLE = "MERGE paragraphs";

    private final List<PdfStructElem> paragraphs;
    private int mergedCount;

    public MergeSplitParagraphsFix(List<PdfStructElem> paragraphs) {
        this.paragraphs = List.copyOf(paragraphs);
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (paragraphs.size() < 2
                || !(paragraphs.get(0).getParent() instanceof PdfStructElem parent)) {
            return;
        }
        if (!StructTree.areConsecutiveKids(parent, paragraphs)) {
            logger.debug(
                    "P #{} and following are no longer adjacent; skipping merge",
                    Tag.of(paragraphs.get(0)).objNum());
            return;
        }

        PdfStructElem target = paragraphs.get(0);
        for (PdfStructElem later : paragraphs.subList(1, paragraphs.size())) {
            StructTree.moveKids(later, target);
            parent.removeKid(later);
            mergedCount++;
        }
        StructTree.addToolScribble(target, MERGED_SCRIBBLE);

        logger.debug("Merged {} P tags into #{}", paragraphs.size(), Tag.of(target).objNum());
    }

    @Override
    public String describe() {
        return "Merged " + (mergedCount + 1) + " paragraph fragments into one";
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(), Locus.atElem(ctx, paragraphs.get(0)));
    }

    @Override
    public String groupLabel() {
        return "Merged fragmented paragraphs";
    }

    @Override
    public int resolvedItemCount() {
        return mergedCount;
    }
}
