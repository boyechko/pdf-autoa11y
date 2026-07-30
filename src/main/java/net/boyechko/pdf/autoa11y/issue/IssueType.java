// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

/** Represents the type of accessibility issue found in a PDF document. */
public enum IssueType {
    // Fatal issues (processing cannot continue)
    IMAGE_ONLY_DOCUMENT("image-only document with no text"),

    // Document-level issues
    FALSE_PDFUA_CONFORMANCE("false PDF/UA conformance claim"),
    LANGUAGE_NOT_SET("language not set"),
    NOT_TAGGED_PDF("PDF not marked as tagged"),
    NO_STRUCT_TREE("structure tree missing"),
    TAB_ORDER_NOT_SET("tab order not set"),
    LIGATURE_MAPPING_BROKEN("fonts with broken ligature mappings"),

    // Tag Issues
    TAG_UNKNOWN_ROLE("tags with unknown roles"),
    TAG_WRONG_PARENT("tags with wrong parent"),
    TAG_WRONG_CHILD("tags with wrong children"),
    TAG_WRONG_CHILD_COUNT("tags with wrong child count"),
    TAG_WRONG_CHILD_PATTERN("tags with wrong child pattern"),
    FIGURE_WITH_TEXT("Figure elements containing text"),
    FIGURE_MISSING_ALT("images missing alt text"),
    EMPTY_LINK_TAG("Link elements without description"),
    INVALID_LINK_URI("Link elements with invalid URIs"),

    // Structure Issues
    MISSING_DOCUMENT_ELEMENT("no Document element under /StructTreeRoot"),
    PAGE_PARTS_NOT_NORMALIZED("elements not grouped into page-level Part elements"),
    STRUCT_TREE_OUT_OF_ORDER("structure tree siblings out of reading order"),
    NEEDLESS_NESTING("unnecessary Part/Sect/Art/Div grouping elements"),
    MISTAGGED_ARTIFACT("tagged content that should be artifacts"),
    MISARTIFACTED_TEXT("artifact blocks containing text that should be tagged"),
    UNMARKED_LINK("Link annotations not tagged"),
    UNEXPECTED_WIDGET("non-functional Widget annotations"),
    ORPHANED_CONTENT("orphaned MCRs"),
    INCONSISTENT_PARENT_TREE("marked content missing from the page ParentTree"),
    EMPTY_ELEMENT("empty structure elements"),
    LIST_TAGGED_AS_PARAGRAPHS("list tagged as a series of paragraphs"),
    SUBLIST_TAGGED_AS_PARAGRAPHS("sublist tagged as a series of paragraphs"),
    LIST_SPLIT_BY_SUBLIST("list split in two around a sublist"),
    BULLET_ALIGNED_KIDS_IN_ELEMENT("bullet-aligned content inside non-list element"),
    PARAGRAPH_OF_LINKS("paragraphs containing only links"),
    ROLEMAP_PRESENT("RoleMap present in structure tree root"),
    SCRIBBLED_INSTRUCTION("elements with structural-instruction scribbles"),
    STALE_SCRIBBLE("elements with stale workflow scribbles in /T"),
    NAMED_DESTINATIONS_PRESENT("named destinations in /Catalog /Names /Dests"),
    WEB_CAPTURES_NOT_GROUPED("Web Capture URLs not wrapped in Article elements"),
    WEB_CAPTURES_BADLY_ORDERED("Web Capture pages not in configured URL order"),
    MISTAGGED_HEADING("headings tagged as paragraphs"),
    IMPROPERLY_NESTED_HEADING("headings with skipped or out-of-order levels"),
    MIXED_FONT_MARKED_CONTENT("marked content lumping differently-sized lines");

    private final String groupLabel;

    IssueType(String groupLabel) {
        this.groupLabel = groupLabel;
    }

    public String groupLabel() {
        return groupLabel;
    }
}
