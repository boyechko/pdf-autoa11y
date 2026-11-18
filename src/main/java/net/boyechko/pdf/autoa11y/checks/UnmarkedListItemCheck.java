// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.MarkedContent;
import net.boyechko.pdf.autoa11y.document.PageContent;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.fixes.ListItemScribble;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Detects list items set without a marker: content tagged as a list that the page never bulleted or
 * numbered, such as headings or paragraphs tagged as items long ago.
 *
 * <p>For now the check only reports what it reads, stamping every list with its item and marker
 * counts, e.g. {@code ITEMS 4, MARKERS 1}. A marker is either a vector bullet glyph drawn on one of
 * the list's lines or a bullet character or enumerator set as text in an item's Lbl. Each list
 * counts only its own items and markers: lists are left children-first, so a sublist claims its
 * glyphs before its parent list counts the rest, and only the list's own items are read for labels.
 */
public class UnmarkedListItemCheck extends StructTreeCheck {

    /** Maximum y-distance (pt) between a bullet and a text line for the bullet to sit on it. */
    private static final float SAME_LINE_TOLERANCE = 3.0f;

    /** Characters that mark an item as bulleted when they make up its whole Lbl. */
    private static final Set<String> BULLET_CHARACTERS =
            Set.of("•", "◦", "▪", "▫", "■", "□", "●", "○", "‣", "⁃", "∙", "–", "-");

    /** An enumerator making up a whole Lbl: {@code 1.}, {@code 12}, {@code a)}, {@code (iv)}. */
    private static final Pattern ENUMERATOR =
            Pattern.compile("\\(?(?:\\d{1,3}|[A-Za-z]|[ivxlcdm]+|[IVXLCDM]+)[.)]|\\d{1,3}");

    /** A bullet glyph together with the page it was drawn on. */
    private record PageBullet(int page, Content.BulletPosition position) {}

    private final IssueList issues = new IssueList();
    private final Set<PageBullet> claimed = new HashSet<>();

    @Override
    public String name() {
        return "Unmarked List Item Check";
    }

    @Override
    public String description() {
        return "Detects list items set without a bullet or number";
    }

    @Override
    public void leaveElement(Visit visit) {
        if (!visit.hasRole("L")) return;
        Set<PageBullet> drawn = bulletsOn(visit);
        drawn.removeAll(claimed);
        claimed.addAll(drawn);
        ListItemScribble.refreshCount(visit.node(), drawn.size() + labelMarkersOf(visit));
        markDocChanged();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** Returns the bullets sitting on any of the element's text lines, on every page it touches. */
    private static Set<PageBullet> bulletsOn(Visit visit) {
        PageContent pages = visit.docCtx().pages();
        Set<PageBullet> found = new HashSet<>();
        for (int pageNum : pagesTouchedBy(visit)) {
            List<Content.BulletPosition> bullets = pages.bullets(pageNum);
            for (Rectangle line : pages.lineBounds(visit.node(), pageNum)) {
                bullets.stream()
                        .filter(bullet -> sitsOn(bullet, line))
                        .forEach(bullet -> found.add(new PageBullet(pageNum, bullet)));
            }
        }
        return found;
    }

    /** Counts the list's own items whose Lbl is a bullet character or enumerator set as text. */
    private static long labelMarkersOf(Visit visit) {
        PageContent pages = visit.docCtx().pages();
        return visit.tag().children().stream()
                .filter(item -> "LI".equals(item.role()))
                .filter(item -> hasMarkerLabel(item, pages))
                .count();
    }

    private static boolean hasMarkerLabel(Tag item, PageContent pages) {
        return item.children().stream()
                .filter(kid -> "Lbl".equals(kid.role()))
                .map(label -> pages.text(label.elem()).strip())
                .anyMatch(
                        text ->
                                BULLET_CHARACTERS.contains(text)
                                        || ENUMERATOR.matcher(text).matches());
    }

    private static boolean sitsOn(Content.BulletPosition bullet, Rectangle line) {
        return bullet.y() >= line.getBottom() - SAME_LINE_TOLERANCE
                && bullet.y() <= line.getTop() + SAME_LINE_TOLERANCE;
    }

    private static List<Integer> pagesTouchedBy(Visit visit) {
        return visit.tag().descendantMarkedContent().stream()
                .map(MarkedContent::page)
                .filter(pageNum -> pageNum > 0)
                .distinct()
                .sorted()
                .toList();
    }
}
