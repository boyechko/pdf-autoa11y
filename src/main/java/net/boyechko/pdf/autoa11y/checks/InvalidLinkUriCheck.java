/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Link;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/** Detects Link elements whose URI is not a plausible http(s) web address. */
public class InvalidLinkUriCheck extends StructTreeCheck {

    /** Tag prefix written to /T on offending elements and cleared on each run. */
    static final String CHECK_SCRIBBLE_PREFIX = "LINK_URI";

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Invalid Link URI Check";
    }

    @Override
    public String description() {
        return "Link elements should point to valid web addresses";
    }

    @Override
    public void beforeTraversal(DocContext docCtx) {
        PdfStructTreeRoot root = docCtx.doc().getStructTreeRoot();
        if (root != null && StructTree.clearScribbleSegmentsInTree(root, CHECK_SCRIBBLE_PREFIX)) {
            docCtx.markDirty();
        }
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        if (!issues.isEmpty()) {
            docCtx.markDirty();
        }
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        PdfStructElem node = ctx.node();
        if (!PdfName.Link.equals(node.getRole())) {
            return true;
        }

        List<PdfObjRef> objRefs = StructTree.childrenOf(node, PdfObjRef.class);
        if (objRefs.isEmpty()) {
            return true;
        }
        PdfDictionary annotDict = objRefs.getFirst().getReferencedObject();
        if (annotDict == null) {
            return true;
        }

        String uri = Link.getUri(annotDict);
        if (uri == null || Link.isValidWebUri(uri)) {
            return true;
        }

        String scribble = CHECK_SCRIBBLE_PREFIX + " invalid: " + uri;
        StructTree.addScribble(node, scribble);

        issues.add(
                new Issue(
                        IssueType.INVALID_LINK_URI,
                        IssueSev.WARNING,
                        locAtElem(ctx),
                        "Link has invalid URI: " + uri));
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }
}
