package net.boyechko.pdf.autoa11y;

public final class Issue {
    private final IssueType type;
    private final IssueSeverity severity;
    private final IssueLocation where;
    private final String message;
    private final IssueFix fix; // may be null if no automatic fix exists

    private boolean resolved;
    private boolean failed;
    private String resolutionNote;

    public Issue(IssueType type, IssueSeverity sev, IssueLocation where, String message, IssueFix fix) {
        this.type = type; this.severity = sev; this.where = where; this.message = message; this.fix = fix;
    }

    public IssueType type() { return type; }
    public IssueSeverity severity() { return severity; }
    public IssueLocation where() { return where; }
    public String message() { return message; }
    public IssueFix fix() { return fix; }

    public boolean resolved() { return resolved; }
    public boolean failed() { return failed; }
    public String resolutionNote() { return resolutionNote; }
    public void markResolved(String note) { this.resolved = true; this.resolutionNote = note; }
    public void markFailed(String note) { this.failed = true; this.resolutionNote = note; }
}