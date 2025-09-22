package net.boyechko.pdf.autoa11y;

public enum IssueType {
    LANGUAGE_NOT_SET,
    NOT_TAGGED_PDF,
    TAB_ORDER_NOT_SET,

    // Tag Issues
    TAG_WRONG_PARENT,           // Child has wrong parent type
    TAG_WRONG_CHILD,            // Parent contains a child type it shouldn't
    TAG_WRONG_CHILD_COUNT,      // Parent has too many/few children
    TAG_WRONG_CHILD_PATTERN     // Parent's children don't match pattern
}
