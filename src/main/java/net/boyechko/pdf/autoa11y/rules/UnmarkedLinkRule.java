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
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import java.util.Locale;
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.CreateLinkTag;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects Link annotations that are not associated with Link structure elements. */
public class UnmarkedLinkRule implements Rule {
    private static final int MAX_OBJ_NUMBER_WIDTH = 4;

    @Override
    public String name() {
        return "Unmarked Link Rule";
    }

    @Override
    public String passedMessage() {
        return "All link annotations are tagged";
    }

    @Override
    public String failedMessage() {
        return "Some link annotations are not tagged";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        IssueList issues = new IssueList();

        for (int pageNum = 1; pageNum <= ctx.doc().getNumberOfPages(); pageNum++) {
            PdfPage page = ctx.doc().getPage(pageNum);

            for (PdfAnnotation annot : page.getAnnotations()) {
                PdfDictionary annotDict = annot.getPdfObject();
                PdfName subtype = annotDict.getAsName(PdfName.Subtype);

                if (PdfName.Link.equals(subtype)) {
                    // Skip if annotation already has a StructParent (already tagged)
                    if (annotDict.containsKey(PdfName.StructParent)) {
                        continue;
                    }

                    String uri = getAnnotationUri(annotDict);
                    String objNumber =
                            String.valueOf(annotDict.getIndirectReference().getObjNumber());
                    String description = UnmarkedLinkRule.buildDescription(objNumber, uri, pageNum);

                    IssueFix fix = new CreateLinkTag(annotDict, pageNum);
                    Issue issue =
                            new Issue(
                                    IssueType.UNMARKED_LINK,
                                    IssueSeverity.ERROR,
                                    new IssueLocation(pageNum, "Page " + pageNum),
                                    "Untagged " + description,
                                    fix);
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    /** Builds a description for a link annotation. */
    public static String buildDescription(String objNumber, String uri, int pageNum) {
        String objLabel =
                String.format(Locale.ROOT, "%" + MAX_OBJ_NUMBER_WIDTH + "s", "#" + objNumber);
        StringBuilder sb = new StringBuilder("Link annotation ").append(objLabel);

        if (pageNum > 0) {
            sb.append(" (p. ").append(pageNum).append(")");
        }

        if (uri != null) {
            sb.append(" to ").append(McidTextExtractor.truncateText(uri));
        }

        return sb.toString();
    }

    public static String getAnnotationUri(PdfDictionary annotDict) {
        PdfDictionary action = annotDict.getAsDictionary(PdfName.A);
        if (action != null) {
            PdfName actionType = action.getAsName(PdfName.S);
            if (PdfName.URI.equals(actionType)) {
                var uriObj = action.get(PdfName.URI);
                if (uriObj != null) {
                    return uriObj.toString();
                }
            }
        }
        return null;
    }
}
