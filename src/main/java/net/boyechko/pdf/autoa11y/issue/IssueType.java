/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
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
package net.boyechko.pdf.autoa11y.issue;

/** Represents the type of an accessibility issue found in a PDF document. */
public enum IssueType {
    // Fatal issues (processing cannot continue)
    IMAGE_ONLY_DOCUMENT("image-only document with no text"),

    // Document-level issues
    FALSE_PDFUA_CONFORMANCE("false PDF/UA conformance claim"),
    LANGUAGE_NOT_SET("language not set"),
    NOT_TAGGED_PDF("PDF not tagged"),
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
    EMPTY_LINK_TAG("empty link tags"),

    // Structure Issues
    MISSING_DOCUMENT_ELEMENT("structure tree root has no Document element"),
    PAGE_PARTS_NOT_NORMALIZED("elements not grouped into page-level Part elements"),
    NEEDLESS_NESTING("unnecessary Part/Sect/Art/Div wrappers"),
    MISTAGGED_ARTIFACT("tagged content that should be artifacts"),
    UNMARKED_LINK("Link annotations not tagged"),
    UNEXPECTED_WIDGET("unexpected Widget annotations"),
    EMPTY_ELEMENT("empty structure elements"),
    LIST_TAGGED_AS_PARAGRAPHS("list tagged as a series of paragraphs"),
    BULLET_ALIGNED_KIDS_IN_ELEMENT("bullet-aligned content inside non-list element"),
    PARAGRAPH_OF_LINKS("paragraphs containing only links");

    private final String groupLabel;

    IssueType(String groupLabel) {
        this.groupLabel = groupLabel;
    }

    public String groupLabel() {
        return groupLabel;
    }
}
