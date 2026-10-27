// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Geometry;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.EmptyLinkTagFix;
import net.boyechko.pdf.autoa11y.fixes.ReuniteLinkFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Detects Link tags that are missing link descriptions, where the description can be found under
 * the link annotation: either as marked content just before the Link, or as a Link elsewhere on the
 * same page that holds the text but no annotation. The latter pairs are matched only after the
 * walk, since a tagger may place the annotation half anywhere, often after its text.
 */
public class EmptyLinkTagCheck extends StructTreeCheck {

    private final IssueList issues = new IssueList();
    private final Set<Integer> fixedBySiblingMcr = new HashSet<>();
    private final Map<Integer, List<PdfStructElem>> annotationOnlyLinks = new LinkedHashMap<>();
    private final Map<Integer, List<PdfStructElem>> textOnlyLinks = new LinkedHashMap<>();

    @Override
    public String name() {
        return "Empty Link Tag Check";
    }

    @Override
    public String description() {
        return "Link elements should contain link description";
    }

    @Override
    public boolean enterElement(Visit visit) {
        findLinksAfterTheirText(visit);
        if (PdfName.Link.equals(visit.node().getRole())) {
            collectLinkHalf(visit);
        }
        return true;
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        annotationOnlyLinks.forEach(
                (pageNum, annotLinks) -> reuniteLinksOnPage(docCtx, pageNum, annotLinks));
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Flags empty Links whose description is the marked content right before them. */
    private void findLinksAfterTheirText(Visit visit) {
        // Get the raw IStructureNode children (includes MCRs, not just PdfStructElems)
        List<IStructureNode> kids = visit.node().getKids();
        if (kids == null || kids.isEmpty()) {
            return;
        }

        for (int i = 1; i < kids.size(); i++) {
            IStructureNode prev = kids.get(i - 1);
            IStructureNode curr = kids.get(i);

            if (!(curr instanceof PdfStructElem linkElem)) {
                continue;
            }
            if (!linkElem.getRole().equals(PdfName.Link)
                    || !StructTree.childrenOf(linkElem, PdfMcr.class).isEmpty()) {
                continue;
            }
            if (!(prev instanceof PdfMcr mcr) || mcr.getMcid() < 0) {
                continue;
            }

            PdfDictionary annotDict = annotationOf(linkElem);
            if (annotDict == null) {
                continue;
            }

            int pageNum = visit.page();
            if (pageNum <= 0) {
                continue;
            }

            Map<Integer, Rectangle> mcidBounds = visit.docCtx().pages().bounds(pageNum);
            Rectangle mcrRect = mcidBounds.get(mcr.getMcid());
            Rectangle annotRect = Geometry.getAnnotationBounds(annotDict);
            if (!Geometry.boundsSimilar(mcrRect, annotRect)) {
                continue;
            }

            IssueFix fix = new EmptyLinkTagFix(linkElem, mcr.getMcid(), pageNum);
            Issue issue =
                    new Issue(
                            IssueType.EMPTY_LINK_TAG,
                            IssueSev.WARNING,
                            Locus.atPage(pageNum),
                            "Link element (after/inside "
                                    + prev.getRole().getValue()
                                    + ") is missing link description",
                            fix);
            issues.add(issue);
            fixedBySiblingMcr.add(StructTree.objNum(linkElem));
        }
    }

    /** Files a Link holding only an annotation, or only text, under its page for later pairing. */
    private void collectLinkHalf(Visit visit) {
        PdfStructElem link = visit.node();
        if (fixedBySiblingMcr.contains(visit.objNum()) || visit.page() <= 0) {
            return;
        }
        boolean hasText = !StructTree.descendantsOf(link, PdfMcr.class).isEmpty();
        boolean hasAnnotation = !StructTree.descendantsOf(link, PdfObjRef.class).isEmpty();
        if (hasAnnotation && !hasText && annotationOf(link) != null) {
            annotationOnlyLinks.computeIfAbsent(visit.page(), k -> new ArrayList<>()).add(link);
        } else if (hasText && !hasAnnotation) {
            textOnlyLinks.computeIfAbsent(visit.page(), k -> new ArrayList<>()).add(link);
        }
    }

    /**
     * Pairs each annotation-only Link with the one text-only Link on the page whose text lies under
     * the annotation; ambiguous or unmatched annotations are left alone.
     */
    private void reuniteLinksOnPage(DocContext ctx, int pageNum, List<PdfStructElem> annotLinks) {
        List<PdfStructElem> candidates =
                new ArrayList<>(textOnlyLinks.getOrDefault(pageNum, List.of()));
        for (PdfStructElem annotLink : annotLinks) {
            Rectangle annotRect = Geometry.getAnnotationBounds(annotationOf(annotLink));
            List<PdfStructElem> matches =
                    candidates.stream()
                            .filter(
                                    textLink ->
                                            Geometry.boundsSimilar(
                                                    Content.getBoundsForElement(
                                                            textLink, ctx, pageNum),
                                                    annotRect))
                            .toList();
            if (matches.size() != 1) {
                continue;
            }
            PdfStructElem textLink = matches.getFirst();
            candidates.remove(textLink);
            issues.add(
                    new Issue(
                            IssueType.EMPTY_LINK_TAG,
                            IssueSev.WARNING,
                            Locus.atElem(ctx, annotLink),
                            "Link element is missing link description, which is tagged"
                                    + " elsewhere on the page",
                            new ReuniteLinkFix(annotLink, textLink, pageNum)));
        }
    }

    /** Returns the annotation dictionary of the Link's first OBJR, or null if there is none. */
    private static PdfDictionary annotationOf(PdfStructElem link) {
        List<PdfObjRef> objRefs = StructTree.childrenOf(link, PdfObjRef.class);
        return objRefs.isEmpty() ? null : objRefs.getFirst().getReferencedObject();
    }
}
