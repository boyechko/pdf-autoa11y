/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Typed domain values extracted from a PDF document.
 *
 * <p>Each variant represents a semantically distinct quantity (object number, MCID, page number,
 * etc.) that would otherwise be a bare primitive. The {@link #render()} method provides a canonical
 * compact string representation for use in labels and diagnostics.
 */
public sealed interface DocValue {

    /** A PDF indirect object number, rendered as {@code #42}. */
    record ObjNum(int value) implements DocValue {
        /** Extracts the object number from a structure element, or null if unavailable. */
        public static ObjNum of(PdfStructElem elem) {
            int obj = StructTree.objNum(elem);
            return obj >= 0 ? new ObjNum(obj) : null;
        }

        /** Extracts the object number from an indirect reference. */
        public static ObjNum of(PdfIndirectReference ref) {
            return new ObjNum(ref.getObjNumber());
        }

        @Override
        public String toString() {
            return "#" + value;
        }
    }

    /** A marked content identifier, rendered as {@code &7}. */
    record Mcid(int value) implements DocValue {
        @Override
        public String toString() {
            return "&" + value;
        }
    }

    /** A marked content reference with its content kinds, rendered as {@code text+image &7}. */
    record Mcr(int mcid, Set<Content.ContentKind> kinds) implements DocValue {
        @Override
        public String toString() {
            if (kinds == null || kinds.isEmpty()) return "unknown";
            List<String> labels = new ArrayList<>();
            if (kinds.contains(Content.ContentKind.TEXT)) labels.add("text");
            if (kinds.contains(Content.ContentKind.IMAGE)) labels.add("image");
            if (kinds.contains(Content.ContentKind.PATH)) labels.add("path");
            String kindStr = labels.isEmpty() ? "unknown" : String.join("+", labels);
            return kindStr + " " + new Mcid(mcid);
        }
    }

    /** A page number, rendered as {@code p. 5}. */
    record PageNum(int value) implements DocValue {
        @Override
        public String toString() {
            return "p. " + value;
        }
    }

    /** A structure element annotation (/T key), rendered as {@code "some title"}. */
    record Scribble(String value) implements DocValue {
        /** Extracts the string value of /T key from the element. */
        public static Scribble of(PdfStructElem elem) {
            PdfString t = elem.getPdfObject().getAsString(PdfName.T);
            if (t == null) {
                return null;
            }

            // Strip null terminator that Acrobat seems to add to PDF strings
            String value = t.toUnicodeString().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
            if (value.isEmpty()) {
                return null;
            }
            return new Scribble(value.replaceFirst(StructTree.SCRIBBLE_PREFIX, ""));
        }

        @Override
        public String toString() {
            return "\"" + StructTree.SCRIBBLE_PREFIX + value + "\"";
        }
    }

    /** A link annotation, rendered as {@code link #57}. */
    record Link(int objNum) implements DocValue {
        @Override
        public String toString() {
            return "link " + new ObjNum(objNum);
        }
    }

    /** A widget annotation, rendered as {@code widget #102}. */
    record Widget(int objNum) implements DocValue {
        @Override
        public String toString() {
            return "widget " + new ObjNum(objNum);
        }
    }

    /** A Link annotation's destination — either a URI or a GoTo target. */
    sealed interface Destination extends DocValue
            permits Destination.Uri, Destination.GoToPage, Destination.GoToNamed {

        /** A URI destination, rendered as {@code /URI (https://example.com)}. */
        record Uri(String url) implements Destination {
            @Override
            public String toString() {
                return "/URI (" + url + ")";
            }
        }

        /** A GoTo destination resolved to a page number, rendered as {@code /GoTo p52}. */
        record GoToPage(int pageNum) implements Destination {
            @Override
            public String toString() {
                return "/GoTo p" + pageNum;
            }
        }

        /** A named GoTo destination (left unresolved), rendered as {@code /GoTo MyChapter}. */
        record GoToNamed(String name) implements Destination {
            @Override
            public String toString() {
                return "/GoTo " + name;
            }
        }
    }

    /**
     * Extracts the destination of a Link annotation OBJR. Returns null if the OBJR is not a Link,
     * has no resolvable destination, or its action type is not /URI or /GoTo. Per PDF spec
     * §12.6.4.11, an /A action takes precedence over /Dest when both are present.
     */
    static Destination destinationOf(PdfObjRef objRef) {
        PdfDictionary annot = objRef.getReferencedObject();
        if (annot == null) return null;
        if (!PdfName.Link.equals(annot.getAsName(PdfName.Subtype))) return null;

        PdfDictionary action = annot.getAsDictionary(PdfName.A);
        if (action != null) {
            PdfName actionType = action.getAsName(PdfName.S);
            if (PdfName.URI.equals(actionType)) {
                PdfString uri = action.getAsString(PdfName.URI);
                return uri != null ? new Destination.Uri(uri.toUnicodeString()) : null;
            }
            if (PdfName.GoTo.equals(actionType)) {
                return resolveGoToDest(action.get(PdfName.D));
            }
            return null;
        }

        return resolveGoToDest(annot.get(PdfName.Dest));
    }

    /** Resolves the /D value of a GoTo action, or a /Dest value, into a typed Destination. */
    private static Destination resolveGoToDest(PdfObject dest) {
        if (dest == null) return null;
        if (dest instanceof PdfArray arr && !arr.isEmpty()) {
            PdfDictionary pageDict = arr.getAsDictionary(0);
            if (pageDict == null) return null;
            PdfIndirectReference ref = pageDict.getIndirectReference();
            if (ref == null) return null;
            PdfDocument doc = ref.getDocument();
            if (doc == null) return null;
            int pageNum = doc.getPageNumber(pageDict);
            return pageNum > 0 ? new Destination.GoToPage(pageNum) : null;
        }
        if (dest instanceof PdfName name) return new Destination.GoToNamed(name.getValue());
        if (dest instanceof PdfString str) return new Destination.GoToNamed(str.toUnicodeString());
        return null;
    }

    /**
     * Extracts a typed annotation entity from an OBJR, based on its /Subtype. Returns null if the
     * referenced object is unavailable.
     */
    static DocValue annotOf(PdfObjRef objRef) {
        PdfDictionary refObj = objRef.getReferencedObject();
        if (refObj == null) return null;
        PdfIndirectReference ref = refObj.getIndirectReference();
        int objNum = ref != null ? ref.getObjNumber() : -1;
        PdfName subtype = refObj.getAsName(PdfName.Subtype);
        if (subtype == null) return objNum >= 0 ? new ObjNum(objNum) : null;
        String name = subtype.getValue();
        if ("Link".equals(name)) return new Link(objNum);
        if ("Widget".equals(name)) return new Widget(objNum);
        // Fallback: render as "subtype #objNum"
        return new AnnotOther(name.toLowerCase(), objNum);
    }

    /** An annotation with an uncommon subtype, rendered as {@code stamp #88}. */
    record AnnotOther(String subtype, int objNum) implements DocValue {
        @Override
        public String toString() {
            return objNum >= 0 ? subtype + " " + new ObjNum(objNum) : subtype;
        }
    }

    /** A structure element role, rendered as-is (e.g. {@code P}, {@code Document}). */
    record Role(String value) implements DocValue {
        /** Extracts the role of the structure element. */
        public static Role of(PdfStructElem elem) {
            PdfName role = elem.getRole();
            return role != null ? new DocValue.Role(role.getValue()) : null;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
