package net.boyechko.pdf.autoa11y.rules;

import com.itextpdf.kernel.pdf.*;
import net.boyechko.pdf.autoa11y.*;

public class TaggedPdfRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override
    public String name() {
        return "Tagged PDF";
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
            // Document is already marked as tagged PDF
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
                        return "Set /MarkInfo /Marked true";
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
                        "✗ Document is not marked as tagged PDF",
                        fix);
        return new IssueList(issue);
    }
}
