/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.visitors;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructureTree;
import net.boyechko.pdf.autoa11y.fixes.children.WrapBulletAlignedKidsInLBody;
import net.boyechko.pdf.autoa11y.fixes.children.WrapParagraphRunInList;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects vector bullet glyphs in the content stream and matches them to tagged structure elements
 * by y-position overlap. Elements aligned with bullets are wrapped in L > LI > LBody structure.
 */
public class BulletGlyphVisitor implements StructureTreeVisitor {

    private static final Logger logger = LoggerFactory.getLogger(BulletGlyphVisitor.class);

    private static final Set<String> CONTAINER_ROLES = Set.of("Part", "Sect", "Div", "Document");
    private static final Set<String> SKIP_ROLES =
            Set.of(
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
        return "Bullet Glyph Visitor";
    }

    @Override
    public String description() {
        return "Detects vector bullet glyphs near elements that should be lists";
    }

    @Override
    public void leaveElement(VisitorContext ctx) {
        if (!CONTAINER_ROLES.contains(ctx.role())) {
            return;
        }
        if (ctx.children().isEmpty()) {
            return;
        }

        int pageNum = ctx.getPageNumber();
        if (pageNum <= 0) {
            return;
        }

        List<Content.BulletPosition> bullets =
                ctx.docCtx()
                        .getOrComputeBulletPositions(
                                pageNum,
                                () ->
                                        Content.extractBulletPositionsForPage(
                                                ctx.doc().getPage(pageNum)));

        if (bullets.isEmpty()) {
            return;
        }

        findBulletMatchedRuns(ctx, bullets, pageNum);
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void findBulletMatchedRuns(
            VisitorContext ctx, List<Content.BulletPosition> bullets, int pageNum) {
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

            Rectangle bounds = Content.getBoundsForElement(child, ctx.docCtx(), pageNum);
            if (bounds != null
                    && bounds.getHeight() <= MAX_ELEMENT_HEIGHT
                    && hasBulletAtY(bullets, bounds)) {
                currentRun.add(child);
            } else {
                emitRunIfLongEnough(ctx, currentRun);
                currentRun = new ArrayList<>();

                // Drill into too-tall elements to find bullet-aligned raw kids
                if (bounds != null && bounds.getHeight() > MAX_ELEMENT_HEIGHT) {
                    findBulletAlignedKidsInElement(ctx, child, bullets, pageNum);
                }
            }
        }
        emitRunIfLongEnough(ctx, currentRun);
    }

    private boolean hasBulletAtY(List<Content.BulletPosition> bullets, Rectangle bounds) {
        float bottom = bounds.getBottom() - Y_OVERLAP_TOLERANCE;
        float top = bounds.getTop() + Y_OVERLAP_TOLERANCE;
        return bullets.stream().anyMatch(b -> b.y() >= bottom && b.y() <= top);
    }

    private void emitRunIfLongEnough(VisitorContext ctx, List<PdfStructElem> run) {
        if (run.size() < MIN_RUN_LENGTH) {
            return;
        }

        IssueFix fix = new WrapParagraphRunInList(ctx.node(), run);
        Issue issue =
                new Issue(
                        IssueType.LIST_TAGGED_AS_PARAGRAPHS,
                        IssueSev.WARNING,
                        IssueLoc.atElem(ctx.node()),
                        run.size() + " elements aligned with vector bullet glyphs",
                        fix);
        issues.add(issue);

        logger.debug(
                "Detected {} elements with bullet glyphs under obj. #{} on page {}",
                run.size(),
                StructureTree.objNum(ctx.node()),
                ctx.getPageNumber());
    }

    /** A group of consecutive raw kid indices that align with the same bullet y-position. */
    private record BulletAlignedGroup(List<Integer> kidIndices, float bulletY) {}

    /**
     * Drills into an element's raw kids to find groups that align with bullet positions. Each group
     * becomes a WrapBulletAlignedKidsInLBody fix.
     */
    private void findBulletAlignedKidsInElement(
            VisitorContext ctx,
            PdfStructElem element,
            List<Content.BulletPosition> bullets,
            int pageNum) {
        List<IStructureNode> rawKids = element.getKids();
        if (rawKids == null || rawKids.isEmpty()) {
            return;
        }

        List<BulletAlignedGroup> groups = new ArrayList<>();
        List<Integer> currentGroup = new ArrayList<>();
        float currentBulletY = Float.NaN;

        for (int i = 0; i < rawKids.size(); i++) {
            IStructureNode kid = rawKids.get(i);
            Rectangle kidBounds = boundsForRawKid(kid, ctx, pageNum);

            if (kidBounds == null) {
                // Skip kids without bounds (e.g., OBJRs)
                continue;
            }

            Content.BulletPosition matchedBullet = findMatchingBullet(bullets, kidBounds);
            if (matchedBullet != null) {
                if (currentGroup.isEmpty()
                        || Math.abs(currentBulletY - matchedBullet.y()) < Y_OVERLAP_TOLERANCE) {
                    currentGroup.add(i);
                    currentBulletY = matchedBullet.y();
                } else {
                    // Different bullet — flush current group and start new one
                    groups.add(
                            new BulletAlignedGroup(new ArrayList<>(currentGroup), currentBulletY));
                    currentGroup.clear();
                    currentGroup.add(i);
                    currentBulletY = matchedBullet.y();
                }
            } else {
                if (!currentGroup.isEmpty()) {
                    groups.add(
                            new BulletAlignedGroup(new ArrayList<>(currentGroup), currentBulletY));
                    currentGroup.clear();
                    currentBulletY = Float.NaN;
                }
            }
        }

        // Flush final group
        if (!currentGroup.isEmpty()) {
            groups.add(new BulletAlignedGroup(new ArrayList<>(currentGroup), currentBulletY));
        }

        // Emit issues for each group (in reverse order so index adjustments are safe)
        for (int g = groups.size() - 1; g >= 0; g--) {
            BulletAlignedGroup group = groups.get(g);
            IssueFix fix =
                    new WrapBulletAlignedKidsInLBody(element, group.kidIndices(), group.bulletY());
            Issue issue =
                    new Issue(
                            IssueType.BULLET_ALIGNED_KIDS_IN_ELEMENT,
                            IssueSev.WARNING,
                            IssueLoc.atElem(element),
                            group.kidIndices().size()
                                    + " raw kids aligned with bullet glyph inside "
                                    + element.getRole().getValue(),
                            fix);
            issues.add(issue);

            logger.debug(
                    "Found {} bullet-aligned raw kids in obj. #{} (bulletY={})",
                    group.kidIndices().size(),
                    StructureTree.objNum(element),
                    String.format("%.1f", group.bulletY()));
        }
    }

    /** Computes bounds for a single raw kid (MCR or struct element). */
    private Rectangle boundsForRawKid(IStructureNode kid, VisitorContext ctx, int pageNum) {
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
