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

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.boyechko.pdf.autoa11y.document.*;
import net.boyechko.pdf.autoa11y.fixes.MistaggedHeadingFix;
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
 * Detects P (and mis-leveled H1-H6) elements that should be headings based on font size analysis.
 *
 * <p>A font-size histogram is built locally for each {@code Art} grouping element: the most common
 * size within the Art is treated as its body text, and larger sizes are ranked into heading levels.
 * Content outside any Art falls back to a document-wide histogram. Scoping per Art prevents the
 * largest display text anywhere in a long document from crowding out the heading sizes that a given
 * Article actually uses.
 */
public class MistaggedHeadingCheck extends StructTreeCheck {
    private static final Logger logger = LoggerFactory.getLogger(MistaggedHeadingCheck.class);

    private static final List<PdfName> HEADING_LEVELS =
            List.of(PdfName.H1, PdfName.H2, PdfName.H3, PdfName.H4, PdfName.H5, PdfName.H6);
    private static final String ART_ROLE = "Art";
    private static final int TRUNCATED_LENGTH = 20;

    /** Mood mark for a tool finding: informational, never executed (cf. '!' for instructions). */
    private static final String FINDING_MARK = "?";

    private final IssueList issues = new IssueList();
    private final Deque<HeadingScope> scopeStack = new ArrayDeque<>();
    private HeadingScope docScope = HeadingScope.EMPTY;

    @Override
    public String name() {
        return "Mistagged Heading Check";
    }

    @Override
    public String description() {
        return "Headings tagged as P based on font size analysis";
    }

    @Override
    public void beforeTraversal(DocContext docCtx) {
        docScope = buildScope(charCountsForDocument(docCtx));
        logScope("Document", docScope);
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        String role = StructTree.mappedRole(node);

        if (ART_ROLE.equals(role)) {
            HeadingScope scope = buildScope(charCountsForElement(node, ctx.docCtx()));
            scopeStack.push(scope);
            logScope(
                    Label.of(new DocValue.Role("Art"))
                            .add("(")
                            .separator("")
                            .add(new DocValue.PageNum(StructTree.pageOf(node, ctx.docCtx())))
                            .add(")")
                            .toString(),
                    scope);
        }

        if (!MistaggedHeadingFix.ELEMENTS_TO_TAG.contains(role)) {
            return true;
        }

        HeadingScope scope = currentScope();
        if (scope.isEmpty()) {
            return true;
        }

        Float dominantSize = dominantSize(charCountsForElement(node, ctx.docCtx()));
        if (dominantSize == null) {
            return true;
        }

        PdfName headingLevel = scope.levelFor(dominantSize);
        if (headingLevel == null) {
            return true;
        }

        // Heading levels must not skip a level (H3 -> H5 with no H4 between). Flag for review
        // rather than retagging.
        int level = HEADING_LEVELS.indexOf(headingLevel) + 1;

        // At most one H1 per scope: the first top-size heading is the Article title; later headings
        // at that same size are subordinate, so cap them at H2 rather than introducing a second H1.
        if (level == 1) {
            if (scope.hasH1()) {
                level = 2;
                headingLevel = PdfName.H2;
            } else {
                scope.markH1();
            }
        }

        int expectedLevel = scope.prevLevel() + 1;
        if (level > expectedLevel) {
            // A tool-authored finding (":?"): the level the size suggests, why it was rejected,
            // and the measured size. It is informational only -- never an executable instruction,
            // since retagging to this level would itself be improperly nested.
            StructTree.setToolScribble(
                    node,
                    FINDING_MARK
                            + headingLevel.getValue()
                            + " (expected H"
                            + expectedLevel
                            + ")"
                            + StructTree.SCRIBBLE_SEPARATOR
                            + dominantSize
                            + "pt");
            issues.add(
                    new Issue(
                            IssueType.IMPROPERLY_NESTED_HEADING,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Improperly nested "
                                    + headingLevel.getValue()
                                    + ": \""
                                    + getTruncatedText(ctx)
                                    + "\"",
                            null));
            return true;
        }
        scope.setPrevLevel(level);

        // Already at the correct level: nothing to fix.
        if (headingLevel.getValue().equals(role)) {
            return true;
        }

        IssueFix fix = new MistaggedHeadingFix(node, headingLevel);
        issues.add(
                new Issue(
                        IssueType.MISTAGGED_HEADING,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "Mistagged "
                                + headingLevel.getValue()
                                + ": \""
                                + getTruncatedText(ctx)
                                + "\"",
                        fix));

        return true;
    }

    @Override
    public void leaveElement(StructTreeContext ctx) {
        if (ART_ROLE.equals(StructTree.mappedRole(ctx.node())) && !scopeStack.isEmpty()) {
            scopeStack.pop();
        }
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    // == Scope ===========================================================

    /**
     * Font-size histogram for one grouping element: body text size, the heading-level map, and the
     * deepest valid heading level seen so far in this scope (for nesting checks).
     */
    private static final class HeadingScope {
        static final HeadingScope EMPTY = new HeadingScope(Map.of(), 0f);

        private final Map<Float, PdfName> headingMap;
        private final float bodySize;
        private int prevLevel = 0;
        private boolean h1Assigned = false;

        HeadingScope(Map<Float, PdfName> headingMap, float bodySize) {
            this.headingMap = headingMap;
            this.bodySize = bodySize;
        }

        Map<Float, PdfName> headingMap() {
            return headingMap;
        }

        float bodySize() {
            return bodySize;
        }

        /** Deepest properly nested heading level seen so far (0 = none yet). */
        int prevLevel() {
            return prevLevel;
        }

        void setPrevLevel(int level) {
            prevLevel = level;
        }

        /** Whether an H1 has already been assigned in this scope (at most one per Art). */
        boolean hasH1() {
            return h1Assigned;
        }

        void markH1() {
            h1Assigned = true;
        }

        boolean isEmpty() {
            return headingMap.isEmpty();
        }

        PdfName levelFor(float size) {
            return headingMap.get(size);
        }
    }

    /** Returns the innermost Art scope, or the document scope when outside any Art. */
    private HeadingScope currentScope() {
        return scopeStack.isEmpty() ? docScope : scopeStack.peek();
    }

    /**
     * Builds a scope from a font-size histogram: the most-character size becomes body text, and
     * larger sizes are ranked descending into H1, H2, ... up to the available heading levels.
     */
    private HeadingScope buildScope(Map<Float, Long> charCountBySize) {
        if (charCountBySize.isEmpty()) {
            return HeadingScope.EMPTY;
        }

        float bodySize =
                charCountBySize.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(0f);

        List<Float> headingSizes =
                charCountBySize.keySet().stream()
                        .filter(size -> size > bodySize + 0.5f)
                        .sorted(Comparator.reverseOrder())
                        .toList();

        Map<Float, PdfName> headingMap = new HashMap<>();
        for (int i = 0; i < headingSizes.size() && i < HEADING_LEVELS.size(); i++) {
            headingMap.put(headingSizes.get(i), HEADING_LEVELS.get(i));
        }
        return new HeadingScope(headingMap, bodySize);
    }

    // == Histograms ======================================================

    /** Accumulates a font-size -> character-count histogram across every page. */
    private Map<Float, Long> charCountsForDocument(DocContext ctx) {
        Map<Float, Long> charCountBySize = new HashMap<>();
        int totalPages = ctx.doc().getNumberOfPages();
        for (int p = 1; p <= totalPages; p++) {
            for (Content.McidContent mc : ctx.getMcidContent(p).values()) {
                accumulate(charCountBySize, mc);
            }
        }
        return charCountBySize;
    }

    /** Accumulates a font-size -> character-count histogram over an element's subtree. */
    private Map<Float, Long> charCountsForElement(PdfStructElem node, DocContext ctx) {
        Map<Float, Long> charCountBySize = new HashMap<>();
        for (PdfMcr mcr : StructTree.descendantsOf(node, PdfMcr.class)) {
            if (mcr instanceof PdfObjRef) {
                continue;
            }
            int mcid = mcr.getMcid();
            if (mcid < 0) {
                continue;
            }
            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) {
                continue;
            }
            int pageNum = ctx.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                continue;
            }
            Content.McidContent mc = ctx.getMcidContent(pageNum).get(mcid);
            if (mc != null) {
                accumulate(charCountBySize, mc);
            }
        }
        return charCountBySize;
    }

    /** Adds an MCID's text spans into a font-size histogram. */
    private static void accumulate(Map<Float, Long> charCountBySize, Content.McidContent mc) {
        for (Content.TextSpan span : mc.spans()) {
            charCountBySize.merge(
                    roundSize(span.fontSize()), (long) span.text().length(), Long::sum);
        }
    }

    /** Returns the rounded font size with the most characters, or null when there is none. */
    private static Float dominantSize(Map<Float, Long> charCountBySize) {
        return charCountBySize.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Returns a truncated text content of the given @code{StructTreeContext}. */
    private String getTruncatedText(StructTreeContext ctx) {
        int pageNum = ctx.getPageNumber();
        if (pageNum <= 0) {
            return "";
        }

        String text = Content.getTextForElement(ctx.node(), ctx.docCtx(), pageNum);
        return text.length() > TRUNCATED_LENGTH ? text.substring(0, TRUNCATED_LENGTH) + "…" : text;
    }

    /** Rounds font size to the nearest 0.25pt to handle floating-point noise. */
    private static float roundSize(float size) {
        return Math.round(size * 4) / 4.0f;
    }

    private static void logScope(String label, HeadingScope scope) {
        if (scope.isEmpty()) {
            return;
        }
        String levels =
                scope.headingMap().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                        .map(e -> String.format("%s=%5.2fpt", e.getValue().getValue(), e.getKey()))
                        .collect(Collectors.joining("  "));
        logger.debug("{}: body={}pt, headings: {}", label, scope.bodySize(), levels);
    }
}
