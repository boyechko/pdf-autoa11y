// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
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

    /**
     * Reads an element's /T, stripped of the control characters Acrobat appends, or null when the
     * key is absent or holds nothing but those characters.
     */
    private static String titleValue(PdfStructElem elem) {
        PdfString t = elem.getPdfObject().getAsString(PdfName.T);
        if (t == null) {
            return null;
        }
        String value = t.toUnicodeString().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return value.isEmpty() ? null : value;
    }

    /**
     * A plain /T title — one written by the authoring tool or by hand, carrying no scribble prefix
     * — rendered as {@code title "Chapter 1"}. The leading word keeps it distinct from a scribble
     * both to the eye and to the annotated-diagram parser, which reads a quoted value only when it
     * directly follows the object number.
     */
    record Title(String value) implements DocValue {
        /** Reads the element's plain title, or null when its /T is absent or holds a scribble. */
        public static Title of(PdfStructElem elem) {
            String value = titleValue(elem);
            if (value == null || value.startsWith(StructTree.SCRIBBLE_PREFIX)) {
                return null;
            }
            return new Title(value);
        }

        @Override
        public String toString() {
            return "title \"" + value + "\"";
        }
    }

    /** A structure element annotation (/T key), rendered as {@code "some title"}. */
    record Scribble(String value) implements DocValue {
        /**
         * Reads the element's scribble, or null when its /T is absent, empty, or a plain title
         * rather than a scribble. Only a /T carrying the scribble prefix is a scribble; a
         * hand-authored title such as {@code Chapter 1} is a {@link Title}.
         */
        public static Scribble of(PdfStructElem elem) {
            String value = titleValue(elem);
            if (value == null || !value.startsWith(StructTree.SCRIBBLE_PREFIX)) {
                return null;
            }
            String body = value.substring(StructTree.SCRIBBLE_PREFIX.length());
            return body.isEmpty() ? null : new Scribble(body);
        }

        /** Returns the raw {@code /T} storage value (prefix included, no surrounding quotes). */
        public String rawValue() {
            return StructTree.SCRIBBLE_PREFIX + value;
        }

        /** Whether this scribble was authored by the tool (carries the leading tool mark). */
        public boolean toolAuthored() {
            return value.startsWith(StructTree.SCRIBBLE_TOOL_MARK);
        }

        /** The scribble content with any leading tool-authorship mark removed. */
        public String body() {
            return toolAuthored() ? value.substring(StructTree.SCRIBBLE_TOOL_MARK.length()) : value;
        }

        /** Splits the scribble body into its segments on the shared separator. */
        public List<String> segments() {
            return List.of(
                    body().split(java.util.regex.Pattern.quote(StructTree.SCRIBBLE_SEPARATOR)));
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
                return resolveDestination(action.get(PdfName.D));
            }
            return null;
        }

        return resolveDestination(annot.get(PdfName.Dest));
    }

    /**
     * Extracts the URI a Link annotation originally pointed to, preserved in its /PA dictionary by
     * Web Capture (or similar HTML-to-PDF tools) when the link's /A action was rewritten from a URI
     * to an internal /GoTo. Returns null if the OBJR is not a Link, has no /PA, or /PA is not a URI
     * action. See PDF spec §12.5.6.5.
     */
    static Destination originalUriOf(PdfObjRef objRef) {
        PdfDictionary annot = objRef.getReferencedObject();
        if (annot == null) return null;
        if (!PdfName.Link.equals(annot.getAsName(PdfName.Subtype))) return null;

        PdfDictionary pa = annot.getAsDictionary(PdfName.PA);
        if (pa == null) return null;
        if (!PdfName.URI.equals(pa.getAsName(PdfName.S))) return null;

        PdfString uri = pa.getAsString(PdfName.URI);
        return uri != null ? new Destination.Uri(uri.toUnicodeString()) : null;
    }

    /**
     * Resolves a raw PDF destination value into a typed {@link Destination}. The input may be the
     * value of a /D action key, a /Dest annotation key, or an entry from the /Catalog /Names /Dests
     * name tree. Indirect references and {@code <</D ...>>} wrappers are dereferenced
     * transparently. Returns null if the value is null, malformed, or its target page cannot be
     * resolved.
     */
    static Destination resolveDestination(PdfObject dest) {
        if (dest == null) return null;
        if (dest instanceof PdfIndirectReference ref) {
            return resolveDestination(ref.getRefersTo());
        }
        if (dest instanceof PdfDictionary dict) {
            return resolveDestination(dict.get(PdfName.D));
        }
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
