// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges two lists that read as one. The second list's items move to the end of the first and the
 * emptied second list is removed.
 *
 * <p>Applies only if the lists are immediate siblings at apply time (earlier fixes, such as nesting
 * the intervening sublist, must have vacated the space between them); otherwise it is skipped. Runs
 * after the wrap fixes so the adjacency check sees their result.
 */
public final class MergeAdjacentListsFix implements IssueFix {

    private static final Logger logger = LoggerFactory.getLogger(MergeAdjacentListsFix.class);

    private final PdfStructElem firstList;
    private final PdfStructElem secondList;

    public MergeAdjacentListsFix(PdfStructElem firstList, PdfStructElem secondList) {
        this.firstList = firstList;
        this.secondList = secondList;
    }

    @Override
    public int priority() {
        return 30; // last: the wrap fixes (10, 20) must first make the lists adjacent
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (!(firstList.getParent() instanceof PdfStructElem container)) {
            return;
        }

        // Merge only when the lists ended up as immediate siblings
        if (!Tag.of(secondList).equals(Tag.of(firstList).nextSibling())) {
            logger.debug(
                    "Lists #{} and #{} are not adjacent; skipping merge",
                    Tag.of(firstList).objNum(),
                    Tag.of(secondList).objNum());
            return;
        }

        // Move only struct-elem items; an L with loose MCR kids is left alone
        List<IStructureNode> items = secondList.getKids();
        if (items.stream().anyMatch(kid -> !(kid instanceof PdfStructElem))) {
            logger.debug(
                    "List #{} has non-element kids; skipping merge", Tag.of(secondList).objNum());
            return;
        }

        for (IStructureNode item : items) {
            PdfStructElem li = (PdfStructElem) item;
            secondList.removeKid(li);
            firstList.addKid(li);
        }
        container.removeKid(secondList);
        ListItemScribble.update(firstList, "MERGE lists");

        logger.debug(
                "Merged {} items of list #{} into list #{}",
                items.size(),
                Tag.of(secondList).objNum(),
                Tag.of(firstList).objNum());
    }

    @Override
    public String describe() {
        return "Merged adjacent sibling lists into one list";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
