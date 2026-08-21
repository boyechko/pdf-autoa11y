// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/**
 * Shared bullet-glyph geometry for the bullet evidence passes of {@link MistaggedListCheck}: page
 * bullet positions (cached on the DocContext), matching a bullet to an element's bounds, and
 * reading the bullet x-position of a list's items.
 */
final class BulletMatcher {

    /** Maximum y-distance (pt) between a bullet and an element's bounds to count as a match. */
    static final float Y_OVERLAP_TOLERANCE = 3.0f;

    /** Maximum bullet x-difference (pt) for bullets to count as the same list level. */
    static final float SAME_LEVEL_TOLERANCE = 3.0f;

    /** Minimum bullet indent (pt) for content to count as a sublist of the preceding list. */
    static final float SUBLIST_INDENT_MIN = 10.0f;

    private BulletMatcher() {}

    /** Returns the (cached) bullet glyph positions for a page. */
    static List<Content.BulletPosition> bulletsFor(StructTreeContext ctx, int pageNum) {
        return ctx.docCtx()
                .getOrComputeBulletPositions(
                        pageNum,
                        () -> Content.extractBulletPositionsForPage(ctx.doc().getPage(pageNum)));
    }

    /** Finds the bullet that matches a given bounding box, or null if none matches. */
    static Content.BulletPosition findMatchingBullet(
            List<Content.BulletPosition> bullets, Rectangle bounds) {
        float bottom = bounds.getBottom() - Y_OVERLAP_TOLERANCE;
        float top = bounds.getTop() + Y_OVERLAP_TOLERANCE;
        return bullets.stream()
                .filter(b -> b.y() >= bottom && b.y() <= top)
                .findFirst()
                .orElse(null);
    }

    /** Returns the bullet x-position matched to a list's first or last item, or NaN. */
    static float listItemBulletX(StructTreeContext ctx, PdfStructElem list, boolean lastItem) {
        List<PdfStructElem> items =
                StructTree.childrenOf(list, PdfStructElem.class).stream()
                        .filter(kid -> "LI".equals(StructTree.mappedRole(kid)))
                        .toList();
        if (items.isEmpty()) {
            return Float.NaN;
        }
        PdfStructElem li = items.get(lastItem ? items.size() - 1 : 0);
        int pageNum = StructTree.pageOf(li, ctx.docCtx());
        if (pageNum <= 0) {
            return Float.NaN;
        }
        Rectangle bounds = Content.getBoundsForElement(li, ctx.docCtx(), pageNum);
        if (bounds == null) {
            return Float.NaN;
        }
        Content.BulletPosition bullet = findMatchingBullet(bulletsFor(ctx, pageNum), bounds);
        return bullet != null ? bullet.x() : Float.NaN;
    }
}
