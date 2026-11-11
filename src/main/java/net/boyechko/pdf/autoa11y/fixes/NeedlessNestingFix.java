// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flattens needless Part/Sect/Div wrappers by promoting their children to the parent element. */
public class NeedlessNestingFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(NeedlessNestingFix.class);

    private final List<PdfStructElem> elementsToFlatten;
    private int flattened = 0;

    public NeedlessNestingFix(List<PdfStructElem> elementsToFlatten) {
        this.elementsToFlatten = new ArrayList<>(elementsToFlatten);
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        // Process in reverse order to handle nested wrappers correctly
        for (int i = elementsToFlatten.size() - 1; i >= 0; i--) {
            PdfStructElem wrapper = elementsToFlatten.get(i);
            flattenElement(wrapper);
        }
    }

    /** Puts the wrapper's element kids in its place; a wrapper no longer in the tree is skipped. */
    private void flattenElement(PdfStructElem wrapper) {
        Tag tag = Tag.of(wrapper);
        if (!tag.isAttached()) {
            logger.debug("Skipping flatten for {}: not among its parent's kids", tag);
            return;
        }
        List<Tag> children = tag.children();
        tag.replaceWith(children);
        flattened++;
        logger.debug("Flattened {} with {} children", tag, children.size());
    }

    @Override
    public String describe() {
        return "Flattened " + flattened + " grouping element(s)";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), Locus.none());
    }

    @Override
    public int resolvedItemCount() {
        return flattened;
    }
}
