// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FormatTest {

    @Test
    void truncateReturnsNullForNull() {
        assertNull(Format.truncate(null));
        assertNull(Format.truncate(null, 20));
    }

    @Test
    void truncateLeavesShortTextUnchanged() {
        assertEquals("Hello", Format.truncate("Hello", 10));
        assertEquals("0123456789", Format.truncate("0123456789", 10));
    }

    @Test
    void truncateBreaksAtLastWordBoundaryWhenInUpperHalf() {
        // "The quick brown fox jumps over the lazy dog" (length 43)
        // maxWidth 20: substring(0, 19) is "The quick brown fox", lastSpace is at 15 ("The quick
        // brown")
        // Remaining characters omitted: 43 - 15 = 28
        assertEquals(
                "The quick brown…+28",
                Format.truncate("The quick brown fox jumps over the lazy dog", 20));
    }

    @Test
    void truncateFallsBackToMidWordCutWhenSpaceIsTooEarly() {
        // "Supercalifragilisticexpialidocious" (length 34)
        // maxWidth 10: substring(0, 9) is "Supercali" (length 9), no space >= 5
        // Remaining characters omitted: 34 - 9 = 25
        assertEquals("Supercali…+25", Format.truncate("Supercalifragilisticexpialidocious", 10));
    }

    @Test
    void truncateStripsTrailingWhitespaceBeforeEllipsis() {
        // "Hello      world" (length 16)
        // maxWidth 10: substring(0, 9) is "Hello    ", lastSpace is 8 >= 5 -> "Hello   " ->
        // stripped to "Hello" (length 5)
        // Remaining characters omitted: 16 - 5 = 11
        assertEquals("Hello…+11", Format.truncate("Hello      world", 10));
    }

    @Test
    void truncateAtSmallestTruncatingLengthReportsTwoOmittedCharacters() {
        // "01234567890" (length 11) is the shortest input that truncates at maxWidth 10
        // substring(0, 9) is "012345678", no space to break on
        // Remaining characters omitted: 11 - 9 = 2
        assertEquals("012345678\u2026+2", Format.truncate("01234567890", 10));
    }

    @Test
    void truncateMayReturnMoreCharactersThanMaxWidth() {
        // The bound applies to the retained text, not to the returned string: the ellipsis and
        // the omitted-character count are appended on top of it.
        String result = Format.truncate("01234567890", 10);
        assertTrue(result.length() > 10);
    }

    @Test
    void truncateUsesDefaultMaxWidth() {
        String input = "This is a sentence that is longer than thirty characters in total.";
        String expected = Format.truncate(input, Format.TRUNCATE_MAX_WIDTH);
        assertEquals(expected, Format.truncate(input));
    }
}
