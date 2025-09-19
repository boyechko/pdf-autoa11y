package net.boyechko.pdf.autoa11y;

public enum IssueType {
    LANGUAGE_NOT_SET,
    NOT_TAGGED_PDF,
    TAB_ORDER_NOT_SET,

    // Tag Issues
    TAG_PARENT_MISMATCH,
    TAG_CARDINALITY_VIOLATION,
    TAG_ILLEGAL_CHILD,
    TAG_ORDER_VIOLATION
}
