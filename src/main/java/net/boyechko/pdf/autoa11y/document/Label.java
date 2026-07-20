// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import java.util.ArrayList;
import java.util.List;

/** Builds compact labels from optional parts, skipping nulls and empty strings. */
public final class Label {
    private final String subject;
    private final List<String> parts = new ArrayList<>();
    private String separator = " ";
    private String open = "";
    private String close = "";

    private Label(DocValue subject) {
        this.subject = subject.toString();
    }

    /** Creates a label with the given token as the main subject. */
    public static Label of(DocValue subject) {
        return new Label(subject);
    }

    /** Adds a token to the label, ignored if null. */
    public Label add(DocValue part) {
        if (part != null) {
            String s = part.toString();
            if (!s.isEmpty()) parts.add(s);
        }
        return this;
    }

    /** Adds a raw string to the label, ignored if null or empty. */
    public Label add(String part) {
        if (part != null && !part.isEmpty()) parts.add(part);
        return this;
    }

    /** Sets the separator between parts (default: space). */
    public Label separator(String sep) {
        this.separator = sep;
        return this;
    }

    /** Wraps the parts in the given open/close delimiters. */
    public Label wrap(String open, String close) {
        this.open = open;
        this.close = close;
        return this;
    }

    /** Builds the label string. Parts are joined and wrapped only if non-empty. */
    @Override
    public String toString() {
        if (parts.isEmpty()) return subject;
        return subject + " " + open + String.join(separator, parts) + close;
    }
}
