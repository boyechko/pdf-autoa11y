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
package net.boyechko.pdf.autoa11y.validation.visitors;

import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.fixes.ConvertToArtifact;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/**
 * Visitor that detects tagged content that should be artifacts, such as footers containing URLs and
 * timestamps that are decorative rather than semantic content.
 */
public class MistaggedArtifactVisitor implements StructureTreeVisitor {

    private static final Pattern FOOTER_URL_TIMESTAMP =
            Pattern.compile(
                    "https?://[^\\s]+.*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TIMESTAMP_ONLY =
            Pattern.compile(
                    "^\\s*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_NUMBER =
            Pattern.compile("^\\s*(Page\\s+)?\\d+\\s*(of\\s+\\d+)?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> CHECKABLE_ROLES =
            Set.of("P", "Link", "Span", "Figure", "Lbl", "LBody");

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Mistagged Artifacts Check";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!CHECKABLE_ROLES.contains(ctx.role())) {
            return true;
        }

        if (matchesArtifactPattern(ctx)) {
            String textContent = getTextContent(ctx);
            String truncated = truncate(textContent, 40);
            IssueFix fix = new ConvertToArtifact(ctx.node());
            Issue issue =
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSeverity.WARNING,
                            new IssueLocation(ctx.node()),
                            "Tagged content should be artifact: \"" + truncated + "\"",
                            fix);
            issues.add(issue);
            return false;
        }

        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private boolean matchesArtifactPattern(VisitorContext ctx) {
        String combinedText = getTextContent(ctx);
        if (combinedText == null || combinedText.isEmpty()) {
            return false;
        }

        return FOOTER_URL_TIMESTAMP.matcher(combinedText).find()
                || TIMESTAMP_ONLY.matcher(combinedText).matches()
                || PAGE_NUMBER.matcher(combinedText).matches();
    }

    private String getTextContent(VisitorContext ctx) {
        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        collectText(ctx.node(), ctx, pageNumber, text);
        return text.toString().trim();
    }

    private void collectText(
            PdfStructElem elem, VisitorContext ctx, int pageNumber, StringBuilder text) {
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfMcrNumber mcr) {
                    String content =
                            McidTextExtractor.extractTextForMcid(
                                    ctx.doc(), mcr.getMcid(), pageNumber);
                    if (content != null && !content.isEmpty()) {
                        if (text.length() > 0) text.append(" ");
                        text.append(content);
                    }
                } else if (kid instanceof PdfStructElem childElem) {
                    collectText(childElem, ctx, pageNumber, text);
                }
            }
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1) + "...";
    }
}
