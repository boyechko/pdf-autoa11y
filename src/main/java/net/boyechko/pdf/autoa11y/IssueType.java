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
package net.boyechko.pdf.autoa11y;

public enum IssueType {
    LANGUAGE_NOT_SET,
    NOT_TAGGED_PDF,
    NO_STRUCT_TREE,
    TAB_ORDER_NOT_SET,

    // Tag Issues
    TAG_UNKNOWN_ROLE, // Tag has a role not in the schema
    TAG_WRONG_PARENT, // Child has wrong parent type
    TAG_WRONG_CHILD, // Parent contains a child type it shouldn't
    TAG_WRONG_CHILD_COUNT, // Parent has too many/few children
    TAG_WRONG_CHILD_PATTERN // Parent's children don't match pattern
}
