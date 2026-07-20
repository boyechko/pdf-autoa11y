// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/** Detects if the document is marked as tagged PDF. */
public class TaggedPdfCheck extends DocumentCheck {

    @Override
    public String name() {
        return "Tagged PDF Check";
    }

    @Override
    public String description() {
        return "Detects if the document is marked as a tagged PDF";
    }

    @Override
    public String passedMessage() {
        return "Document is marked as tagged PDF";
    }

    @Override
    public String failedMessage() {
        return "Document is not marked as tagged PDF (Marked flag not set in MarkInfo dictionary)";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
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
                    public String describe() {
                        return "Set Marked flag to true in MarkInfo dictionary";
                    }

                    @Override
                    public void apply(DocContext c) {
                        PdfCatalog cat2 = c.doc().getCatalog();
                        PdfDictionary mi2 = cat2.getPdfObject().getAsDictionary(PdfName.MarkInfo);
                        if (mi2 == null) {
                            mi2 = new PdfDictionary();
                            cat2.getPdfObject().put(PdfName.MarkInfo, mi2);
                        }
                        mi2.put(PdfName.Marked, PdfBoolean.TRUE);
                    }
                };

        Issue issue = new Issue(IssueType.NOT_TAGGED_PDF, IssueSev.ERROR, failedMessage(), fix);
        return new IssueList(issue);
    }
}
