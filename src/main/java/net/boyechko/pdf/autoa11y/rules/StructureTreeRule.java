package net.boyechko.pdf.autoa11y.rules;

import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import net.boyechko.pdf.autoa11y.*;

public class StructureTreeRule implements Rule {
    private static final int P_DOC_SETUP = 10; // early phase

    @Override public String name() { return "Tag Structure Present"; }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        PdfStructTreeRoot root = ctx.doc().getStructTreeRoot();
        if (root == null) {
            Issue issue = new Issue(
                IssueType.NO_STRUCT_TREE,
                IssueSeverity.ERROR,
                "✗ Document has no structure tree",
                null
            );
            return new IssueList(issue);
        }
        // Further structure checks would go here

        return new IssueList();
    }

}
