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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a run of consecutive P elements in L > LI > LBody structure. With a {@code nestIntoList}
 * target, the new L becomes a sublist appended inside that list's last LI > LBody instead of a
 * sibling at the run's position.
 */
public final class WrapParagraphRunInList implements IssueFix {
    protected static final Logger logger = LoggerFactory.getLogger(WrapParagraphRunInList.class);

    protected final PdfStructElem parent;
    protected final List<PdfStructElem> kids;
    private final PdfStructElem nestIntoList;

    public WrapParagraphRunInList(PdfStructElem parent, List<PdfStructElem> kids) {
        this(parent, kids, null);
    }

    public WrapParagraphRunInList(
            PdfStructElem parent, List<PdfStructElem> kids, PdfStructElem nestIntoList) {
        this.parent = parent;
        this.kids =
                kids != null
                        ? kids.stream().map(kid -> (PdfStructElem) kid).collect(Collectors.toList())
                        : List.of();
        this.nestIntoList = nestIntoList;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        if (kids.isEmpty()) {
            return;
        }

        PdfStructElem firstP = kids.get(0);
        PdfStructElem actualParent = findParentContaining(firstP);
        if (actualParent == null) {
            logger.debug(
                    "Skipping fix: P element obj. #{} not found in any ancestor's K array "
                            + "(stored parent was obj. #{})",
                    StructTree.objNum(firstP),
                    StructTree.objNum(parent));
            return;
        }

        // Find the position of the first P among the actual parent's kids
        List<IStructureNode> parentKids = actualParent.getKids();
        int insertIndex = -1;
        for (int i = 0; i < parentKids.size(); i++) {
            IStructureNode kid = parentKids.get(i);
            if (kid instanceof PdfStructElem elem && StructTree.isSameElement(elem, firstP)) {
                insertIndex = i;
                break;
            }
        }
        if (insertIndex < 0) {
            return;
        }

        // Remove all P elements from actual parent
        for (PdfStructElem p : kids) {
            actualParent.removeKid(p);
        }

        // Create an L element: nested inside the preceding list item for a sublist,
        // otherwise a sibling at the saved position
        PdfStructElem listElem = new PdfStructElem(ctx.doc(), PdfName.L);
        PdfStructElem sublistHost = findSublistHost();
        if (sublistHost != null) {
            sublistHost.addKid(listElem);
        } else {
            actualParent.addKid(insertIndex, listElem);
        }

        // Build LI > LBody > P structure under L
        for (PdfStructElem p : kids) {
            PdfStructElem li = new PdfStructElem(ctx.doc(), PdfName.LI);
            PdfStructElem lBody = new PdfStructElem(ctx.doc(), PdfName.LBody);

            listElem.addKid(li);
            li.addKid(lBody);
            lBody.addKid(p);
        }

        logger.debug(
                "Wrapped {} P elements in L > LI > LBody under obj. #{}",
                kids.size(),
                StructTree.objNum(actualParent));
    }

    /**
     * Resolves the LBody of the nest target's last LI at apply time, or null when not nesting or
     * when the target no longer has an LI > LBody to host the sublist.
     */
    private PdfStructElem findSublistHost() {
        if (nestIntoList == null) {
            return null;
        }
        PdfStructElem lastLi = lastChildWithRole(nestIntoList, "LI");
        if (lastLi == null) {
            return null;
        }
        return lastChildWithRole(lastLi, "LBody");
    }

    private static PdfStructElem lastChildWithRole(PdfStructElem elem, String role) {
        List<PdfStructElem> children = StructTree.childrenOf(elem, PdfStructElem.class);
        for (int i = children.size() - 1; i >= 0; i--) {
            if (role.equals(StructTree.mappedRole(children.get(i)))) {
                return children.get(i);
            }
        }
        return null;
    }

    /**
     * Finds the actual parent whose K array contains the given element. Starts from the element's
     * /P parent and walks up the ancestor chain. This handles cases where NeedlessNestingFix
     * promoted children but iText's getKids() cache became stale.
     */
    private PdfStructElem findParentContaining(PdfStructElem target) {
        // Start from the target's /P parent, then walk up ancestors
        IStructureNode candidate = target.getParent();
        for (int depth = 0; depth < 10 && candidate instanceof PdfStructElem elem; depth++) {
            for (IStructureNode kid : elem.getKids()) {
                if (kid instanceof PdfStructElem kidElem
                        && StructTree.isSameElement(kidElem, target)) {
                    return elem;
                }
            }
            candidate = elem.getParent();
        }
        return null;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public String describe() {
        return nestIntoList != null
                ? "Retagged suspected sublist of " + kids.size() + " P elements as a nested list"
                : "Retagged suspected list of " + kids.size() + " P elements as a list";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe();
    }
}
