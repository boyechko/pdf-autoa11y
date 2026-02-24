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

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/** Represents the location of an accessibility issue found in a PDF document. */
public sealed interface IssueLoc {
    record None() implements IssueLoc {}

    record AtElem(PdfStructElem element) implements IssueLoc {}

    record AtPageNum(int pageNum) implements IssueLoc {}

    record AtObjNum(int objNum, Integer page) implements IssueLoc {}

    static IssueLoc none() {
        return new None();
    }

    static IssueLoc atElem(PdfStructElem e) {
        return new AtElem(e);
    }

    static IssueLoc atPageNum(int page) {
        return new AtPageNum(page);
    }

    static IssueLoc atObjNum(int objNum, Integer page) {
        return new AtObjNum(objNum, page);
    }

    /** Returns page number if available, null otherwise. */
    default Integer page() {
        return switch (this) {
            case AtPageNum(var pn) -> pn;
            case AtObjNum(var o, var p) -> p;
            default -> null;
        };
    }
}
