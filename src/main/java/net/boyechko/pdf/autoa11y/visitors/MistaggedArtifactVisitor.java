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

import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.ContentExtractor;
import net.boyechko.pdf.autoa11y.fixes.ConvertToArtifact;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/** Visitor that detects tagged content that should be artifacts. */
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
        return "Mistagged Artifact Check";
    }

    @Override
    public String description() {
        return "Decorative or noisy content should be artifacted";
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!CHECKABLE_ROLES.contains(ctx.role())) {
            return true;
        }

        if (matchesArtifactPattern(ctx)) {
            String textContent = getTextContent(ctx);
            String truncated =
                    textContent.length() > 40 ? textContent.substring(0, 39) + "…" : textContent;
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
        return ContentExtractor.getTextForElement(ctx.node(), ctx.docCtx(), pageNumber);
    }
}
