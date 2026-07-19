/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges two lists that read as one — typically the halves of a list a tagger split around a
 * sublist. The second list's items move to the end of the first and the emptied second list is
 * removed.
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
        return 30;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (!(firstList.getParent() instanceof PdfStructElem container)) {
            return;
        }

        // Merge only when the lists ended up as immediate siblings
        int firstIndex = StructTree.findKidIndex(container, firstList);
        if (firstIndex < 0) {
            return;
        }
        List<IStructureNode> containerKids = container.getKids();
        if (firstIndex + 1 >= containerKids.size()
                || !(containerKids.get(firstIndex + 1) instanceof PdfStructElem next)
                || !StructTree.isSameElement(next, secondList)) {
            logger.debug(
                    "Lists #{} and #{} are not adjacent; skipping merge",
                    StructTree.objNum(firstList),
                    StructTree.objNum(secondList));
            return;
        }

        // Move only struct-elem items; an L with loose MCR kids is left alone
        List<IStructureNode> items = secondList.getKids();
        if (items.stream().anyMatch(kid -> !(kid instanceof PdfStructElem))) {
            logger.debug(
                    "List #{} has non-element kids; skipping merge", StructTree.objNum(secondList));
            return;
        }

        for (IStructureNode item : items) {
            PdfStructElem li = (PdfStructElem) item;
            secondList.removeKid(li);
            firstList.addKid(li);
        }
        container.removeKid(secondList);
        ListItemScribble.update(firstList);

        logger.debug(
                "Merged {} items of list #{} into list #{}",
                items.size(),
                StructTree.objNum(secondList),
                StructTree.objNum(firstList));
    }

    @Override
    public String describe() {
        return "Merged split list halves into one list";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
