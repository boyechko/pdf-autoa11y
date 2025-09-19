package net.boyechko.pdf.autoa11y;

public final class IssueLocation {
    public final Integer page;        // null if document-level
    public final String objectId;     // optional: struct elem id, annot id, etc.
    public IssueLocation(Integer page, String objectId) {
        this.page = page; this.objectId = objectId;
    }
}