// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

/** Overlapping role classifications; membership is declared explicitly in the tag schema. */
public enum TagType {
    GROUPING,
    BLOCK, // block-level structural elements (BLSEs)
    INLINE, // inline-level structural elements (ILSEs)
    ILLUSTRATION,
    HEADING,
    LIST,
    TABLE
}
