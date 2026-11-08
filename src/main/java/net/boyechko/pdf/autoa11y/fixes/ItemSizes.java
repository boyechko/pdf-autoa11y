// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * How a line split groups an element's lines into items: one item per detected line, optionally
 * checked against an expected total, or explicit per-item line counts in reading order.
 */
record ItemSizes(Integer expectedLines, List<Integer> sizes) {

    /** One item per detected line, however many there are. */
    static ItemSizes eachLine() {
        return new ItemSizes(null, null);
    }

    /** One item per line, refusing unless exactly {@code expectedLines} are detected. */
    static ItemSizes expecting(Integer expectedLines) {
        return new ItemSizes(expectedLines, null);
    }

    /** Explicit per-item line counts in reading order, which must sum to the detected lines. */
    static ItemSizes of(List<Integer> sizes) {
        return new ItemSizes(null, List.copyOf(sizes));
    }

    /**
     * Parses a {@code !SPLIT_LINES} spec: null/blank derives everything, a plain number is the
     * expected total line count (one item per line), and a comma-separated list gives the line
     * count of each item in order (e.g. {@code 1,1,2}; {@code 1x3} is short for {@code 1,1,1}).
     */
    static ItemSizes parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return eachLine();
        }
        if (spec.matches("\\d+")) {
            return expecting(Integer.valueOf(spec));
        }
        if (spec.matches("\\d+(x\\d+)?(\\s*,\\s*\\d+(x\\d+)?)*")) {
            return of(
                    Arrays.stream(spec.split(","))
                            .map(String::strip)
                            .flatMap(ItemSizes::expandGroup)
                            .toList());
        }
        throw new IllegalArgumentException(
                "Bad !SPLIT_LINES spec '" + spec + "'; expected N or N,N,... (NxK repeats N)");
    }

    /** Expands one spec group: {@code N} is one N-line item, {@code NxK} is K such items. */
    private static Stream<Integer> expandGroup(String group) {
        String[] parts = group.split("x");
        int size = Integer.parseInt(parts[0]);
        int repeat = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
        return Collections.nCopies(repeat, size).stream();
    }

    /**
     * Verifies the detected total line count and returns the per-item line counts: the explicit
     * sizes, or one line per item.
     */
    List<Integer> resolve(int lineCount, String pages) {
        if (expectedLines != null && expectedLines < 2) {
            throw new IllegalArgumentException(
                    "Splitting requires at least 2 lines, got " + expectedLines);
        }
        if (sizes != null) {
            int specTotal = sizes.stream().mapToInt(Integer::intValue).sum();
            if (specTotal != lineCount) {
                throw new IllegalStateException(
                        "Item sizes sum to "
                                + specTotal
                                + " lines but found "
                                + lineCount
                                + " on "
                                + pages);
            }
            if (sizes.stream().anyMatch(size -> size < 1)) {
                throw new IllegalArgumentException("Item sizes must be at least 1");
            }
            return sizes;
        }
        if (expectedLines != null && lineCount != expectedLines) {
            throw new IllegalStateException(
                    "Expected " + expectedLines + " lines but found " + lineCount + " on " + pages);
        }
        return Collections.nCopies(lineCount, 1);
    }
}
