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
import net.boyechko.pdf.autoa11y.fixes.RemoveWidgetAnnotation;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects pushbutton Widget annotations that are non-functional remnants from web-to-PDF
 * conversion. These are identified by /FT /Btn with the PushButton flag (bit 17 of /Ff).
 */
public class UnexpectedWidgetRule implements Rule {
    private static final Logger logger = LoggerFactory.getLogger(UnexpectedWidgetRule.class);
    private static final int PUSHBUTTON_FLAG = 0x10000; // bit 17 (PDF spec 1-indexed)

    @Override
    public String name() {
        return "Unexpected Widget Rule";
    }

    @Override
    public String passedMessage() {
        return "No unexpected widget annotations found";
    }

    @Override
    public String failedMessage() {
        return "Document contains unexpected widget annotations";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        IssueList issues = new IssueList();

        for (int pageNum = 1; pageNum <= ctx.doc().getNumberOfPages(); pageNum++) {
            PdfPage page = ctx.doc().getPage(pageNum);

            for (PdfAnnotation annot : page.getAnnotations()) {
                PdfDictionary annotDict = annot.getPdfObject();
                if (isPushbuttonWidget(annotDict)) {
                    int objNum =
                            annotDict.getIndirectReference() != null
                                    ? annotDict.getIndirectReference().getObjNumber()
                                    : 0;
                    logger.debug(
                            "Unexpected Widget annotation found: obj #{}, p. {}", objNum, pageNum);

                    IssueFix fix = new RemoveWidgetAnnotation(annotDict, pageNum);
                    Issue issue =
                            new Issue(
                                    IssueType.UNEXPECTED_WIDGET,
                                    IssueSeverity.ERROR,
                                    new IssueLocation(pageNum, "Page " + pageNum),
                                    "Unexpected Widget annotation found on page " + pageNum,
                                    fix);
                    issues.add(issue);
                }
            }
        }

        return issues;
    }

    private boolean isPushbuttonWidget(PdfDictionary annotDict) {
        PdfName subtype = annotDict.getAsName(PdfName.Subtype);
        if (!PdfName.Widget.equals(subtype)) {
            return false;
        }

        // /FT and /Ff may be on the annotation itself (merged field+widget)
        // or on a parent field dictionary via /Parent
        PdfName fieldType = getInheritedName(annotDict, PdfName.FT);
        if (!PdfName.Btn.equals(fieldType)) {
            return false;
        }

        int ff = getInheritedInt(annotDict, PdfName.Ff);
        logger.debug(
                "Widget obj #{}: FT={}, Ff={}, pushbutton={}",
                annotDict.getIndirectReference() != null
                        ? annotDict.getIndirectReference().getObjNumber()
                        : "?",
                fieldType,
                ff,
                (ff & PUSHBUTTON_FLAG) != 0);
        return (ff & PUSHBUTTON_FLAG) != 0;
    }

    /** Walks the /Parent chain to find an inherited PdfName value. */
    private PdfName getInheritedName(PdfDictionary dict, PdfName key) {
        PdfDictionary current = dict;
        while (current != null) {
            PdfName value = current.getAsName(key);
            if (value != null) {
                return value;
            }
            current = current.getAsDictionary(PdfName.Parent);
        }
        return null;
    }

    /** Walks the /Parent chain to find an inherited integer value. */
    private int getInheritedInt(PdfDictionary dict, PdfName key) {
        PdfDictionary current = dict;
        while (current != null) {
            Integer value = current.getAsInt(key);
            if (value != null) {
                return value;
            }
            current = current.getAsDictionary(PdfName.Parent);
        }
        return 0;
    }
}
