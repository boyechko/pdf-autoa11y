package net.boyechko.pdf.autoa11y;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

public final class IssueLocation {
    private final Integer page;        // null if document-level
    private final PdfStructElem element; // may be null if not applicable
    private final String path;         // optional: struct tree path, object id, etc.

    public IssueLocation() {
        this(null, null, null);
    }

    public IssueLocation(String path) {
        this(null, path, null);
    }

    public IssueLocation(Integer page, String path) {
        this(page, path, null);
    }

    public IssueLocation(PdfStructElem element) {
        this(null, null, element);
    }

    public IssueLocation(PdfStructElem element, String path) {
        this(null, path, element);
    }

    public IssueLocation(Integer page, String path, PdfStructElem element) {
        this.page = page;
        this.path = path;
        this.element = element;
    }

    public Integer page() { return page; }
    public String path() { return path; }
    public PdfStructElem element() { return element; }
}
