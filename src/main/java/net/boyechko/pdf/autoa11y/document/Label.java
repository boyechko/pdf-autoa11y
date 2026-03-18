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
package net.boyechko.pdf.autoa11y.document;

import java.util.ArrayList;
import java.util.List;

/** Builds compact labels from optional parts, skipping nulls and empty strings. */
public final class Label {
    private final String prefix;
    private final List<String> parts = new ArrayList<>();
    private String separator = " ";
    private String open = "";
    private String close = "";

    private Label(String prefix) {
        this.prefix = prefix;
    }

    /** Creates a label with the given prefix (always included). */
    public static Label of(String prefix) {
        return new Label(prefix);
    }

    /** Adds a part to the label, ignored if null or empty. */
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
        if (parts.isEmpty()) return prefix;
        return prefix + " " + open + String.join(separator, parts) + close;
    }
}
