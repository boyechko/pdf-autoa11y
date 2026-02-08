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
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.StructureTree;
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

    private static final float TINY_IMAGE_MAX_SIZE = 20f;

    private static final Set<String> CHECKABLE_ROLES =
            Set.of("P", "Link", "Span", "Figure", "Lbl", "LBody");

    private final IssueList issues = new IssueList();

    private enum ArtifactKind {
        NONE,
        TEXT_PATTERN,
        TINY_IMAGE
    }

    @Override
    public String name() {
        return "Mistagged Artifact Visitor";
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

        String textContent = getTextContent(ctx);
        ArtifactKind artifactKind = detectArtifactKind(ctx, textContent);
        if (artifactKind != ArtifactKind.NONE) {
            IssueFix fix = new ConvertToArtifact(ctx.node());
            String message = artifactMessage(artifactKind, textContent);
            Issue issue =
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSeverity.WARNING,
                            new IssueLocation(ctx.node()),
                            message,
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

    private ArtifactKind detectArtifactKind(VisitorContext ctx, String textContent) {
        if (matchesTextArtifactPattern(textContent)) {
            return ArtifactKind.TEXT_PATTERN;
        }
        if (matchesTinyImagePattern(ctx, textContent)) {
            return ArtifactKind.TINY_IMAGE;
        }
        return ArtifactKind.NONE;
    }

    private boolean matchesTextArtifactPattern(String textContent) {
        if (textContent == null || textContent.isEmpty()) {
            return false;
        }

        return FOOTER_URL_TIMESTAMP.matcher(textContent).find()
                || TIMESTAMP_ONLY.matcher(textContent).matches()
                || PAGE_NUMBER.matcher(textContent).matches();
    }

    private boolean matchesTinyImagePattern(VisitorContext ctx, String textContent) {
        if (textContent != null && !textContent.isBlank()) {
            return false;
        }

        boolean foundTinyMcid = false;
        for (PdfMcr mcr : StructureTree.collectMcrs(ctx.node())) {
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

            String mcidText = ctx.docCtx().getMcidText(pageNum, mcid);
            if (mcidText != null && !mcidText.isBlank()) {
                return false;
            }

            Map<Integer, Rectangle> boundsByMcid =
                    ctx.docCtx()
                            .getOrComputeMcidBounds(
                                    pageNum,
                                    () -> Content.extractBoundsForPage(ctx.doc().getPage(pageNum)));
            Rectangle bounds = boundsByMcid.get(mcid);
            if (bounds == null) {
                continue;
            }
            if (!isTinyBounds(bounds)) {
                return false;
            }
            foundTinyMcid = true;
        }

        return foundTinyMcid;
    }

    private boolean isTinyBounds(Rectangle bounds) {
        float width = Math.abs(bounds.getWidth());
        float height = Math.abs(bounds.getHeight());
        return width > 0
                && height > 0
                && width <= TINY_IMAGE_MAX_SIZE
                && height <= TINY_IMAGE_MAX_SIZE;
    }

    private String artifactMessage(ArtifactKind artifactKind, String textContent) {
        if (artifactKind == ArtifactKind.TINY_IMAGE) {
            return "Tagged tiny image should be artifact";
        }
        String safeText = textContent != null ? textContent : "";
        String truncated = safeText.length() > 40 ? safeText.substring(0, 39) + "…" : safeText;
        return "Tagged content should be artifact: \"" + truncated + "\"";
    }

    private String getTextContent(VisitorContext ctx) {
        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return "";
        }
        return Content.getTextForElement(ctx.node(), ctx.docCtx(), pageNumber);
    }
}
