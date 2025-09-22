package net.boyechko.pdf.autoa11y.rules;

import net.boyechko.pdf.autoa11y.*;
import com.itextpdf.kernel.pdf.*;

public class TabOrderRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override public String name() { return "Tab Order"; }

    @Override
    public IssueList findIssues(ProcessingContext ctx) {
        IssueFix fix = new IssueFix() {
            @Override public int priority() { return P_DOC_SETUP; }
            @Override public String describe() { return "Set document tab order"; }
            @Override public void apply(ProcessingContext c) {
                int pageCount = c.doc().getNumberOfPages();
                for (int i = 1; i <= pageCount; i++) {
                    c.doc().getPage(i).setTabOrder(PdfName.S);
                }
                c.out().println("✓ Set tab order to structure order for all " + pageCount + " pages");
            }
        };

        Issue issue = new Issue(
            IssueType.TAB_ORDER_NOT_SET,
            IssueSeverity.ERROR,
            "✗ Document tab order is not set",
            fix
        );
        return new IssueList(issue);
    }
}
