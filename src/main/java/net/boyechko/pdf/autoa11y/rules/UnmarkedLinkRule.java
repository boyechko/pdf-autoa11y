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
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.fixes.CreateLinkTag;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects Link annotations that are not associated with Link structure elements. */
public class UnmarkedLinkRule implements Rule {
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
                    int objNumber = annotDict.getIndirectReference().getObjNumber();
                    String description = UnmarkedLinkRule.buildDescription(objNumber, uri);

                    IssueFix fix = new CreateLinkTag(annotDict, pageNum);
                    Issue issue =
                            new Issue(
                                    IssueType.UNMARKED_LINK,
                                    IssueSev.ERROR,
                                    IssueLoc.atObjNum(objNumber, pageNum),
                                    "Untagged " + description,
                                    fix);
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    /** Builds a description for a link annotation. */
    public static String buildDescription(int objNumber, String uri) {
        StringBuilder sb = new StringBuilder("Link annotation ").append(Format.objNum(objNumber));

        if (uri != null) {
            String displayUri = uri.length() > 30 ? uri.substring(0, 29) + "…" : uri;
            sb.append(" to ").append(displayUri);
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
