// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.fixes.MergeSplitParagraphsFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

/**
 * Detects a single paragraph that a tagger split across several {@code <P>} tags — typically at a
 * page break or a layout line, leaving a sentence's tail in a P of its own. A screen reader
 * announces each tag as a new paragraph, interrupting the sentence.
 *
 * <p>Two P tags qualify when they are immediately adjacent kids, the first does not end in
 * sentence- or clause-ending punctuation, and the second starts with a lowercase letter. A second P
 * that opens with a letter label ({@code a.}, {@code b)}) or an email address or URL is left alone,
 * since lowercase there does not mean a continued sentence. P tags carrying their own {@code /Alt}
 * or {@code /ActualText} are left alone, since merging would discard one of the text equivalents.
 */
public class FragmentedParagraphCheck extends StructTreeCheck {

    private static final String SENTENCE_END = ".!?:;";
    private static final Pattern LETTER_LABEL = Pattern.compile("^[a-z][.)]\\s");
    private static final Pattern EMAIL_OR_URL = Pattern.compile("^(\\S+@|www\\b|https?:)");

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Fragmented Paragraph Check";
    }

    @Override
    public String description() {
        return "One paragraph split across several P tags should be a single P";
    }

    @Override
    public boolean enterElement(Visit visit) {
        List<IStructureNode> kids = visit.node().getKids();
        if (kids == null || kids.size() < 2) {
            return true;
        }

        List<PdfStructElem> run = new ArrayList<>();
        String previousText = null;
        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfStructElem elem) || !isMergeableParagraph(elem)) {
                emitIfFragmented(visit, run);
                run = new ArrayList<>();
                previousText = null;
                continue;
            }
            String text = Content.getTextForElement(elem, visit.docCtx()).strip();
            if (previousText == null || !continuesSentence(previousText, text)) {
                emitIfFragmented(visit, run);
                run = new ArrayList<>();
            }
            run.add(elem);
            previousText = text;
        }
        emitIfFragmented(visit, run);

        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /** True for a P tag whose own text equivalent would not be lost by a merge. */
    private static boolean isMergeableParagraph(PdfStructElem elem) {
        if (!PdfName.P.equals(elem.getRole())) {
            return false;
        }
        return elem.getPdfObject().get(PdfName.Alt) == null
                && elem.getPdfObject().get(PdfName.ActualText) == null;
    }

    /**
     * True if {@code next} reads as the rest of the sentence {@code previous} leaves unfinished.
     */
    private static boolean continuesSentence(String previous, String next) {
        if (previous.isEmpty() || next.isEmpty()) {
            return false;
        }
        char lastChar = previous.charAt(previous.length() - 1);
        return SENTENCE_END.indexOf(lastChar) < 0
                && Character.isLowerCase(next.charAt(0))
                && !LETTER_LABEL.matcher(next).find()
                && !EMAIL_OR_URL.matcher(next).find();
    }

    private void emitIfFragmented(Visit visit, List<PdfStructElem> run) {
        if (run.size() < 2) {
            return;
        }
        issues.add(
                new Issue(
                        IssueType.PARAGRAPH_FRAGMENTED,
                        IssueSev.WARNING,
                        Locus.atElem(visit.docCtx(), run.get(0)),
                        "One paragraph split across " + run.size() + " P tags",
                        new MergeSplitParagraphsFix(run)));
    }
}
