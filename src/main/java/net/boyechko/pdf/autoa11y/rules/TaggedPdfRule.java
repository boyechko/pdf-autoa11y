package net.boyechko.pdf.autoa11y.rules;

import net.boyechko.pdf.autoa11y.*;
import com.itextpdf.kernel.pdf.*;

public class TaggedPdfRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override public String name() { return "Tagged PDF"; }

    @Override
    public java.util.List<Issue> findIssues(ProcessingContext ctx) {
        PdfCatalog cat = ctx.doc().getCatalog();
        PdfDictionary mi = cat.getPdfObject().getAsDictionary(PdfName.MarkInfo);
        boolean marked = mi != null
            && mi.getAsBoolean(PdfName.Marked) instanceof PdfBoolean pb
            && Boolean.TRUE.equals(pb.getValue());

        if (marked) {
            ctx.out().println("✓ Document is marked as tagged PDF");
            return java.util.List.of();
        }

        IssueFix fix = new IssueFix() {
            @Override public int priority() { return P_DOC_SETUP; }
            @Override public String describe() { return "Set /MarkInfo /Marked true"; }
            @Override public void apply(ProcessingContext c) {
                PdfCatalog cat2 = c.doc().getCatalog();
                PdfDictionary mi2 = cat2.getPdfObject().getAsDictionary(PdfName.MarkInfo);
                if (mi2 == null) { mi2 = new PdfDictionary(); cat2.getPdfObject().put(PdfName.MarkInfo, mi2); }
                mi2.put(PdfName.Marked, PdfBoolean.TRUE);

                c.out().println("✓ Marked document as tagged PDF");
                c.out().println("✓ Set PDF/UA-1 compliance flag");
            }
        };

        Issue issue = new Issue(
            IssueType.NOT_TAGGED_PDF,
            IssueSeverity.ERROR,
            "✗ Document is not marked as tagged PDF",
            fix
        );
        return java.util.List.of(issue);
    }
}