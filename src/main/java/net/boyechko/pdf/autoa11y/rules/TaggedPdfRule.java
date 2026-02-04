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

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects if the document is marked as tagged PDF. */
public class TaggedPdfRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override
    public String name() {
        return "Tagged PDF Rule";
    }

    @Override
    public String passedMessage() {
        return "Document is marked as tagged PDF";
    }

    @Override
    public String failedMessage() {
        return "Document is not marked as tagged PDF";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfCatalog cat = ctx.doc().getCatalog();
        PdfDictionary mi = cat.getPdfObject().getAsDictionary(PdfName.MarkInfo);
        boolean marked =
                mi != null
                        && mi.getAsBoolean(PdfName.Marked) instanceof PdfBoolean pb
                        && Boolean.TRUE.equals(pb.getValue());

        if (marked) {
            return new IssueList();
        }

        IssueFix fix =
                new IssueFix() {
                    @Override
                    public int priority() {
                        return P_DOC_SETUP;
                    }

                    @Override
                    public String describe() {
                        return "Set Marked flag to true in MarkInfo dictionary";
                    }

                    @Override
                    public void apply(DocumentContext c) {
                        PdfCatalog cat2 = c.doc().getCatalog();
                        PdfDictionary mi2 = cat2.getPdfObject().getAsDictionary(PdfName.MarkInfo);
                        if (mi2 == null) {
                            mi2 = new PdfDictionary();
                            cat2.getPdfObject().put(PdfName.MarkInfo, mi2);
                        }
                        mi2.put(PdfName.Marked, PdfBoolean.TRUE);
                    }
                };

        Issue issue =
                new Issue(
                        IssueType.NOT_TAGGED_PDF,
                        IssueSeverity.ERROR,
                        "Document is not marked as tagged PDF (Marked flag not set in MarkInfo dictionary)",
                        fix);
        return new IssueList(issue);
    }
}
