package net.boyechko.pdf.autoa11y;

public interface Rule {
    String name();

    IssueList findIssues(DocumentContext ctx);
}
