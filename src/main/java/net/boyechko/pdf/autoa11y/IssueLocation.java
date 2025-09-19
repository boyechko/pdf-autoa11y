package net.boyechko.pdf.autoa11y;

public final class IssueLocation {
    private final Integer page;        // null if document-level
    private final String path;         // optional: struct tree path, object id, etc.

    public IssueLocation(Integer page, String path) {
        this.page = page;
        this.path = path;
    }

    public Integer page() { return page; }
    public String path() { return path; }
}
