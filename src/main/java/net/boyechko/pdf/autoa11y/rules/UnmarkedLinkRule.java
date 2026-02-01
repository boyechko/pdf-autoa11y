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
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.CreateLinkTag;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/**
 * Detects Link annotations that are not associated with Link structure elements. These unmarked
 * links need to have Link tags created and connected via OBJRs.
 */
public class UnmarkedLinkRule implements Rule {

    @Override
    public String name() {
        return "Unmarked Link Check";
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
                    String description =
                            uri != null
                                    ? "Link annotation not tagged: " + truncate(uri, 50)
                                    : "Link annotation not tagged (page " + pageNum + ")";

                    IssueFix fix = new CreateLinkTag(annotDict, pageNum);
                    Issue issue =
                            new Issue(
                                    IssueType.UNMARKED_LINK,
                                    IssueSeverity.ERROR,
                                    new IssueLocation(pageNum, "Page " + pageNum),
                                    description,
                                    fix);
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    /** Extracts the URI from a Link annotation if present. */
    private String getAnnotationUri(PdfDictionary annotDict) {
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

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1) + "...";
    }
}
