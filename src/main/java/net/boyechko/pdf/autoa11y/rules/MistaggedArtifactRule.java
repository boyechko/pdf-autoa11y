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
package net.boyechko.pdf.autoa11y.rules;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.ConvertToArtifact;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects tagged content that should be artifacted. */
public class MistaggedArtifactRule implements Rule {

    // Pattern: URL followed by timestamp like "https://example.com/path/to/page [1/5/2024 9:00:00
    // PM]"
    private static final Pattern FOOTER_URL_TIMESTAMP =
            Pattern.compile(
                    "https?://[^\\s]+.*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]",
                    Pattern.CASE_INSENSITIVE);

    // Pattern: Just a timestamp in brackets like "[11/15/2024 11:37:19 AM]"
    private static final Pattern TIMESTAMP_ONLY =
            Pattern.compile(
                    "^\\s*\\[\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M\\]\\s*$",
                    Pattern.CASE_INSENSITIVE);

    // Pattern: Page numbers like "Page 1 of 10" or just "1"
    private static final Pattern PAGE_NUMBER =
            Pattern.compile("^\\s*(Page\\s+)?\\d+\\s*(of\\s+\\d+)?\\s*$", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "Decorative or noisy content should be artifacted";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            return new IssueList();
        }

        IssueList issues = new IssueList();
        List<PdfStructElem> elementsToArtifact = new ArrayList<>();

        walkTree(root, ctx, elementsToArtifact);

        for (PdfStructElem elem : elementsToArtifact) {
            String textContent = getTextContent(elem, ctx);
            String truncated = truncate(textContent, 40);
            IssueFix fix = new ConvertToArtifact(elem);
            Issue issue =
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSeverity.WARNING,
                            new IssueLocation(elem),
                            "Tagged content should be artifact: \"" + truncated + "\"",
                            fix);
            issues.add(issue);
        }

        return issues;
    }

    private void walkTree(
            PdfStructTreeRoot root, DocumentContext ctx, List<PdfStructElem> toArtifact) {
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;

        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                checkElement(elem, ctx, toArtifact);
            }
        }
    }

    // Only check these element roles for artifact patterns
    private static final Set<String> ROLES_TO_CHECK =
            Set.of("P", "Link", "Span", "Figure", "Lbl", "LBody");

    private void checkElement(
            PdfStructElem elem, DocumentContext ctx, List<PdfStructElem> toArtifact) {
        String role = elem.getRole() != null ? elem.getRole().getValue() : "";

        if (ROLES_TO_CHECK.contains(role) && matchesArtifactPattern(elem, ctx)) {
            toArtifact.add(elem);
            return;
        }

        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            for (IStructureNode kid : kids) {
                if (kid instanceof PdfStructElem childElem) {
                    checkElement(childElem, ctx, toArtifact);
                }
            }
        }
    }

    private boolean matchesArtifactPattern(PdfStructElem elem, DocumentContext ctx) {
        String combinedText = getTextContent(elem, ctx);
        if (combinedText == null || combinedText.isEmpty()) {
            return false;
        }

        return FOOTER_URL_TIMESTAMP.matcher(combinedText).find()
                || TIMESTAMP_ONLY.matcher(combinedText).matches()
                || PAGE_NUMBER.matcher(combinedText).matches();
    }

    private String getTextContent(PdfStructElem elem, DocumentContext ctx) {
        int pageNumber = getPageNumber(elem, ctx);
        if (pageNumber == 0) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        collectText(elem, ctx, pageNumber, text);
        return text.toString().trim();
    }

    /** Gets full text from MCRs (not truncated summary). */
    private void collectText(
            PdfStructElem elem, DocumentContext ctx, int pageNumber, StringBuilder text) {
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

    // TODO: Move to a utility class
    private int getPageNumber(PdfStructElem elem, DocumentContext ctx) {
        PdfDictionary dict = elem.getPdfObject();
        PdfDictionary pg = dict.getAsDictionary(PdfName.Pg);

        if (pg != null) {
            return ctx.doc().getPageNumber(pg);
        }

        if (elem.getPdfObject().getIndirectReference() != null) {
            int objNum = elem.getPdfObject().getIndirectReference().getObjNumber();
            return ctx.getPageNumber(objNum);
        }

        return 0;
    }

    // TODO: Move to a utility class
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1) + "...";
    }
}
