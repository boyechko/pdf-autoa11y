package net.boyechko.pdf.autoa11y;

public interface Rule {
    String name();
    java.util.List<Issue> findIssues(ProcessingContext ctx);
}