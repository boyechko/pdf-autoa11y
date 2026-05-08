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

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNameTree;
import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.ReorderWebCapturesFix;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;

/**
 * Reorders the document's pages to match a configured URL order from the sidecar. Intended to run
 * <em>before</em> {@link WrapWebCapturesCheck}: with a flat structure, iText's {@code movePage}
 * relocates each page (and its per-page tag subtree) cleanly without splitting multi-page Articles.
 * Once pages are in the desired order, {@link WrapWebCapturesCheck} can wrap them into Articles
 * with clean contiguous page ranges.
 *
 * <p>The configured URL list comes from the sidecar's {@code ReorderWebCapturesCheck:} key, which
 * accepts either a YAML list of URL patterns or a YAML map whose values are URL patterns. YAML
 * insertion order defines the page-block order. Each pattern is matched against entries in {@code
 * /Catalog /Names /URLS} via substring containment, so partial URLs work (e.g., {@code
 * www.uwb.edu/catalog} matches {@code https://www.uwb.edu/catalog/}).
 */
public class ReorderWebCapturesCheck extends DocumentCheck {

    private final List<String> orderedUrls;

    /** No-arg form for the default registry; emits no issues unless configured via sidecar. */
    public ReorderWebCapturesCheck() {
        this(List.of());
    }

    public ReorderWebCapturesCheck(List<String> orderedUrls) {
        this.orderedUrls = List.copyOf(orderedUrls);
    }

    @Override
    public String name() {
        return "Reorder Web Captures";
    }

    @Override
    public String description() {
        return "Reorder pages to follow a configured URL order before wrapping into Articles";
    }

    @Override
    public String passedMessage() {
        return "No URL order configured for Web Capture reorder";
    }

    @Override
    public String failedMessage() {
        return "Web Capture pages need to be reordered to match configured URL order";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        if (orderedUrls.isEmpty()) {
            return new IssueList();
        }
        PdfNameTree urls = ctx.doc().getCatalog().getNameTree(PdfName.URLS);
        if (urls.getNames().isEmpty()) {
            return new IssueList();
        }
        int outOfPosition = ReorderWebCapturesFix.countOutOfPositionPages(ctx.doc(), orderedUrls);
        if (outOfPosition == 0) {
            return new IssueList();
        }
        Issue issue =
                new Issue(
                        IssueType.WEB_CAPTURES_BADLY_ORDERED,
                        IssueSev.INFO,
                        outOfPosition + " page(s) out of configured URL order",
                        new ReorderWebCapturesFix(orderedUrls));
        return new IssueList(issue);
    }
}
