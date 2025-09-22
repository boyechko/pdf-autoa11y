package net.boyechko.pdf.autoa11y.rules;

import net.boyechko.pdf.autoa11y.*;
import com.itextpdf.kernel.pdf.*;

public class LanguageSetRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override public String name() { return "Language Set"; }

    @Override
    public IssueList findIssues(ProcessingContext ctx) {
        PdfCatalog cat = ctx.doc().getCatalog();

        if (cat.getLang() != null) {
            ctx.out().println("✓ Document language (Lang) is set to: " + cat.getLang());
            return new IssueList();
        }

        IssueFix fix = new IssueFix() {
            @Override public int priority() { return P_DOC_SETUP; }
            @Override public String describe() { return "Set document language (Lang)"; }
            @Override public void apply(ProcessingContext c) {
                PdfCatalog cat2 = c.doc().getCatalog();
                cat2.put(PdfName.Lang, new PdfString("en-US")); // Default to English; ideally should be user-specified
                c.out().println("✓ Set document language (Lang)");
            }
        };

        Issue issue = new Issue(
            IssueType.LANGUAGE_NOT_SET,
            IssueSeverity.ERROR,
            "✗ Document language (Lang) is not set",
            fix
        );
        return new IssueList(issue);
    }
}
