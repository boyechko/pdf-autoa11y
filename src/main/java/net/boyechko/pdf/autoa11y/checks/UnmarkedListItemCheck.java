// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.MarkedContent;
import net.boyechko.pdf.autoa11y.document.PageContent;
import net.boyechko.pdf.autoa11y.document.Tag;
import net.boyechko.pdf.autoa11y.fixes.ListItemScribble;
import net.boyechko.pdf.autoa11y.fixes.UnmarkedListItemFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Detects list items set without a marker: content tagged as a list that the page never bulleted or
 * numbered, such as headings or paragraphs tagged as items long ago.
 *
 * <p>An item with no marker of its own is reported and marked {@code !BREAK_OUT}, for {@link
 * ScribbledInstructionCheck} to take out of its list on the next run. An item holding a Lbl of any
 * kind is never reported. Every list is also stamped with its item and marker counts, e.g. {@code
 * items 4, markers 1}.
 *
 * <p>A marker is either a vector bullet glyph drawn on one of the item's lines or a bullet
 * character or enumerator set as text in its Lbl. Lists are left children-first, so a sublist's
 * items claim their glyphs before the item holding the sublist reads its own; within a list, a
 * glyph belongs to the first item that sits on it.
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
        PageContent pages = visit.docCtx().pages();
        long markers = 0;
        for (Tag item : visit.tag().children()) {
            if (!"LI".equals(item.role())) continue;
            Set<PageBullet> drawn = bulletsOn(pages, item);
            drawn.removeAll(claimed);
            claimed.addAll(drawn);
            markers += drawn.size() + (hasMarkerLabel(item, pages) ? 1 : 0);
            if (drawn.isEmpty() && !hasLabel(item)) {
                reportUnmarked(visit, item);
            }
        }
        ListItemScribble.refreshCount(visit.node(), markers);
        markDocChanged();
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void reportUnmarked(Visit visit, Tag item) {
        issues.add(
                new Issue(
                        IssueType.LIST_ITEM_UNMARKED,
                        IssueSev.WARNING,
                        Locus.atElem(visit.docCtx(), item),
                        "List item set without a bullet or number",
                        new UnmarkedListItemFix(item.elem())));
    }

    /** Returns the bullets sitting on any of the element's text lines, on every page it touches. */
    private static Set<PageBullet> bulletsOn(PageContent pages, Tag element) {
        Set<PageBullet> found = new HashSet<>();
        for (int pageNum : pagesTouchedBy(element)) {
            List<Content.BulletPosition> bullets = pages.bullets(pageNum);
            for (Rectangle line : pages.lineBounds(element.elem(), pageNum)) {
                bullets.stream()
                        .filter(bullet -> sitsOn(bullet, line))
                        .forEach(bullet -> found.add(new PageBullet(pageNum, bullet)));
            }
        }
        return found;
    }

    /**
     * Whether the item has a Lbl of any kind. An author who labelled an item meant it as one, and
     * {@code !BREAK_OUT} refuses such items anyway, so they are never reported.
     */
    private static boolean hasLabel(Tag item) {
        return item.children().stream().anyMatch(kid -> "Lbl".equals(kid.role()));
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

    private static List<Integer> pagesTouchedBy(Tag element) {
        return element.descendantMarkedContent().stream()
                .map(MarkedContent::page)
                .filter(pageNum -> pageNum > 0)
                .distinct()
                .sorted()
                .toList();
    }
}
