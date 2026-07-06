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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.WrapBulletAlignedKidsInLBody;
import net.boyechko.pdf.autoa11y.fixes.WrapParagraphRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects vector bullet glyphs in the content stream and matches them to tagged structure elements
 * by y-position overlap. Elements aligned with bullets are wrapped in L > LI > LBody structure.
 */
public class MistaggedBulletedListCheck extends StructTreeCheck {

    private static final Logger logger = LoggerFactory.getLogger(MistaggedBulletedListCheck.class);

    private static final Set<String> CONTAINER_ROLES =
            Set.of("Art", "Part", "Sect", "Div", "Document");
    private static final Set<String> SKIP_ROLES =
            Set.of(
                    "Art",
                    "Part",
                    "Sect",
                    "Div",
                    "Document",
                    "L",
                    "LI",
                    "Lbl",
                    "LBody",
                    "Table",
                    "TR",
                    "TD",
                    "TH",
                    "THead",
                    "TBody",
                    "TFoot");
    private static final int MIN_RUN_LENGTH = 1;
    private static final float Y_OVERLAP_TOLERANCE = 3.0f;

    /** Maximum element height to match — roughly two lines of text. */
    private static final float MAX_ELEMENT_HEIGHT = 30.0f;

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Mistagged Bulleted List Check";
    }

    @Override
    public String description() {
        return "Detects vector bullet glyphs near elements that should be lists";
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        if (!CONTAINER_ROLES.contains(ctx.role())) {
            return;
        }
        if (ctx.children().isEmpty()) {
            return;
        }

        findBulletMatchedRuns(ctx);
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void findBulletMatchedRuns(StructTreeContext ctx) {
        List<PdfStructElem> currentRun = new ArrayList<>();

        for (int i = 0; i < ctx.children().size(); i++) {
            PdfStructElem child = ctx.children().get(i);
            String childRole = ctx.childRoles().get(i);

            // Skip containers, lists, tables — only match leaf-like content elements
            if (SKIP_ROLES.contains(childRole)) {
                emitRunIfLongEnough(ctx, currentRun);
                currentRun = new ArrayList<>();
                continue;
            }

            // Each element is judged against its own page's bullets and bounds;
            // a run may span a page break, since lists do
            int pageNum = StructTree.pageOf(child, ctx.docCtx());
            Rectangle bounds =
                    pageNum > 0 ? Content.getBoundsForElement(child, ctx.docCtx(), pageNum) : null;
            if (bounds != null
                    && bounds.getHeight() <= MAX_ELEMENT_HEIGHT
                    && hasBulletAtY(bulletsFor(ctx, pageNum), bounds)) {
                currentRun.add(child);
            } else {
                emitRunIfLongEnough(ctx, currentRun);
                currentRun = new ArrayList<>();

                // Drill into too-tall elements to find bullet-aligned raw kids
                if (bounds != null && bounds.getHeight() > MAX_ELEMENT_HEIGHT) {
                    findBulletAlignedKidsInElement(ctx, child);
                }
            }
        }
        emitRunIfLongEnough(ctx, currentRun);
    }

    /** Returns the (cached) bullet glyph positions for a page. */
    private List<Content.BulletPosition> bulletsFor(StructTreeContext ctx, int pageNum) {
        return ctx.docCtx()
                .getOrComputeBulletPositions(
                        pageNum,
                        () -> Content.extractBulletPositionsForPage(ctx.doc().getPage(pageNum)));
    }

    private boolean hasBulletAtY(List<Content.BulletPosition> bullets, Rectangle bounds) {
        float bottom = bounds.getBottom() - Y_OVERLAP_TOLERANCE;
        float top = bounds.getTop() + Y_OVERLAP_TOLERANCE;
        return bullets.stream().anyMatch(b -> b.y() >= bottom && b.y() <= top);
    }

    private void emitRunIfLongEnough(StructTreeContext ctx, List<PdfStructElem> run) {
        if (run.size() < MIN_RUN_LENGTH) {
            return;
        }

        IssueFix fix = new WrapParagraphRunInList(ctx.node(), run);
        Issue issue =
                new Issue(
                        IssueType.LIST_TAGGED_AS_PARAGRAPHS,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        run.size() + " elements appear to be a list",
                        fix);
        issues.add(issue);

        logger.debug(
                "Detected {} elements with bullet glyphs under obj. #{} on page {}",
                run.size(),
                StructTree.objNum(ctx.node()),
                StructTree.pageOf(run.get(0), ctx.docCtx()));
    }

    /** A group of consecutive raw kids that align with the same bullet y-position. */
    private record BulletAlignedGroup(List<PdfObject> kidObjects, float bulletY, int pageNum) {}

    /**
     * Drills into an element's raw kids to find groups that align with bullet positions. Each group
     * becomes a WrapBulletAlignedKidsInLBody fix.
     */
    private void findBulletAlignedKidsInElement(StructTreeContext ctx, PdfStructElem element) {
        List<IStructureNode> rawKids = element.getKids();
        if (rawKids == null || rawKids.isEmpty()) {
            return;
        }

        List<BulletAlignedGroup> groups = new ArrayList<>();
        List<PdfObject> currentGroup = new ArrayList<>();
        float currentBulletY = Float.NaN;
        int currentPage = 0;

        for (IStructureNode kid : rawKids) {
            // Each kid is judged against its own page — the element may span pages
            int kidPage = pageOfRawKid(kid, ctx);
            Rectangle kidBounds = kidPage > 0 ? boundsForRawKid(kid, ctx, kidPage) : null;

            if (kidBounds == null) {
                // Skip kids without bounds (e.g., OBJRs)
                continue;
            }

            Content.BulletPosition matchedBullet =
                    findMatchingBullet(bulletsFor(ctx, kidPage), kidBounds);
            if (matchedBullet != null) {
                boolean sameBullet =
                        kidPage == currentPage
                                && Math.abs(currentBulletY - matchedBullet.y())
                                        < Y_OVERLAP_TOLERANCE;
                if (!currentGroup.isEmpty() && !sameBullet) {
                    // Different bullet — flush current group and start new one
                    groups.add(
                            new BulletAlignedGroup(
                                    new ArrayList<>(currentGroup), currentBulletY, currentPage));
                    currentGroup.clear();
                }
                currentGroup.add(pdfObjectOf(kid));
                currentBulletY = matchedBullet.y();
                currentPage = kidPage;
            } else {
                if (!currentGroup.isEmpty()) {
                    groups.add(
                            new BulletAlignedGroup(
                                    new ArrayList<>(currentGroup), currentBulletY, currentPage));
                    currentGroup.clear();
                    currentBulletY = Float.NaN;
                    currentPage = 0;
                }
            }
        }

        // Flush final group
        if (!currentGroup.isEmpty()) {
            groups.add(
                    new BulletAlignedGroup(
                            new ArrayList<>(currentGroup), currentBulletY, currentPage));
        }

        // Emit issues for each group
        for (BulletAlignedGroup group : groups) {
            IssueFix fix =
                    new WrapBulletAlignedKidsInLBody(
                            element, group.kidObjects(), group.bulletY(), group.pageNum());
            Issue issue =
                    new Issue(
                            IssueType.BULLET_ALIGNED_KIDS_IN_ELEMENT,
                            IssueSev.WARNING,
                            locAtElem(ctx, element),
                            group.kidObjects().size()
                                    + " raw kids aligned with bullet glyph inside "
                                    + element.getRole().getValue(),
                            fix);
            issues.add(issue);

            logger.debug(
                    "Found {} bullet-aligned raw kids in obj. #{} (bulletY={})",
                    group.kidObjects().size(),
                    StructTree.objNum(element),
                    String.format("%.1f", group.bulletY()));
        }
    }

    /** Returns the underlying PdfObject for a raw kid (struct element, MCR, or OBJR). */
    private static PdfObject pdfObjectOf(IStructureNode kid) {
        if (kid instanceof PdfStructElem elem) {
            return elem.getPdfObject();
        }
        if (kid instanceof PdfMcr mcr) {
            return mcr.getPdfObject();
        }
        return null;
    }

    /** Returns the page a raw kid's content lives on, or 0 if it cannot be determined. */
    private int pageOfRawKid(IStructureNode kid, StructTreeContext ctx) {
        if (kid instanceof PdfObjRef) {
            return 0;
        } else if (kid instanceof PdfMcr mcr) {
            return StructTree.pageOf(mcr);
        } else if (kid instanceof PdfStructElem structKid) {
            return StructTree.pageOf(structKid, ctx.docCtx());
        }
        return 0;
    }

    /** Computes bounds for a single raw kid (MCR or struct element). */
    private Rectangle boundsForRawKid(IStructureNode kid, StructTreeContext ctx, int pageNum) {
        if (kid instanceof PdfObjRef) {
            return null;
        } else if (kid instanceof PdfMcr mcr) {
            int mcid = mcr.getMcid();
            if (mcid < 0) return null;
            return Content.getBoundsForMcid(ctx.docCtx(), pageNum, mcid);
        } else if (kid instanceof PdfStructElem structKid) {
            return Content.getBoundsForElement(structKid, ctx.docCtx(), pageNum);
        }
        return null;
    }

    /** Finds the bullet that matches a given bounding box, or null if none matches. */
    private Content.BulletPosition findMatchingBullet(
            List<Content.BulletPosition> bullets, Rectangle bounds) {
        float bottom = bounds.getBottom() - Y_OVERLAP_TOLERANCE;
        float top = bounds.getTop() + Y_OVERLAP_TOLERANCE;
        return bullets.stream()
                .filter(b -> b.y() >= bottom && b.y() <= top)
                .findFirst()
                .orElse(null);
    }
}
