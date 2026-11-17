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
            int obj = Tag.of(elem).objNum();
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
     * A plain /T title — one written by the authoring tool or by hand, carrying no scribble prefix
     * — rendered as {@code /T "Chapter 1"}. The leading word keeps it distinct from a scribble both
     * to the eye and to the annotated-diagram parser, which reads a quoted value only when it
     * directly follows the object number.
     */
    record Title(String value) implements DocValue {
        /** Reads the element's plain title, or null when its /T is absent or holds a scribble. */
        public static Title of(PdfStructElem elem) {
            String value = Tag.of(elem).titleText();
            if (value == null || value.startsWith(Scribble.PREFIX)) {
                return null;
            }
            return new Title(value);
        }

        @Override
        public String toString() {
            return "/T \"" + value + "\"";
        }
    }

    /**
     * A workflow note kept in an element's /T, rendered as {@code "__:MERGE lists"}. The value is
     * the /T text after the scribble prefix; the grammar lives here and nowhere else:
     *
     * <ul>
     *   <li>a leading {@link #TOOL_MARK} means the tool wrote it, otherwise the user did;
     *   <li>segments are separated by {@link #SEPARATOR}, and a segment's first word is its head;
     *   <li>a segment opening with {@link #INSTRUCTION_MARK} asks the tool to act, one opening with
     *       {@link #FINDING_MARK} reports something it saw;
     *   <li>a user-authored segment that is exactly {@link #VERIFIED_TOKEN} marks the subtree as
     *       reviewed.
     * </ul>
     *
     * <p>Immutable: {@link #with} and {@link #without} return a new scribble. An element with no
     * scribble has {@link #EMPTY}, never null.
     */
    record Scribble(String value) implements DocValue {
        /** Leads every /T that is a scribble rather than a plain title. */
        public static final String PREFIX = "__";

        public static final String SEPARATOR = " // ";

        /** Leading mark distinguishing a tool-authored scribble from a user-authored one. */
        public static final String TOOL_MARK = ":";

        /** Opens a segment the tool is asked to carry out, e.g. {@code !SET_ROLE H2}. */
        public static final String INSTRUCTION_MARK = "!";

        /** Opens a segment reporting a tool finding, e.g. {@code ?H3 (expected H2)}. */
        public static final String FINDING_MARK = "?";

        /**
         * User-authored segment marking the element (and its subtree) as reviewed and correct, e.g.
         * {@code __OK} or {@code __OK // check figure later}.
         */
        public static final String VERIFIED_TOKEN = "OK";

        /** The scribble of an element that has none. */
        public static final Scribble EMPTY = new Scribble("");

        /**
         * A scribble the tool wrote, marked so it reads as the tool's own rather than the user's.
         */
        public static Scribble tool(String body) {
            return body.isEmpty() ? EMPTY : new Scribble(TOOL_MARK + body);
        }

        /** A scribble as the user would write it by hand. */
        public static Scribble user(String body) {
            return new Scribble(body);
        }

        /**
         * Parses a raw /T value. Anything without the prefix is a plain {@link Title}, and a bare
         * prefix says nothing, so both read as {@link #EMPTY}.
         */
        public static Scribble fromRaw(String raw) {
            if (raw == null || !raw.startsWith(PREFIX)) return EMPTY;
            return new Scribble(raw.substring(PREFIX.length()));
        }

        public boolean isEmpty() {
            return value.isEmpty();
        }

        /** The {@code /T} storage value, prefix included; empty for {@link #EMPTY}. */
        public String rawValue() {
            return isEmpty() ? "" : PREFIX + value;
        }

        /** Whether this scribble was authored by the tool (carries the leading tool mark). */
        public boolean toolAuthored() {
            return value.startsWith(TOOL_MARK);
        }

        /** The scribble content with any leading tool-authorship mark removed. */
        public String body() {
            return toolAuthored() ? value.substring(TOOL_MARK.length()) : value;
        }

        /** The body split on the separator; none for {@link #EMPTY}. */
        public List<String> segments() {
            if (isEmpty()) return List.of();
            return List.of(body().split(java.util.regex.Pattern.quote(SEPARATOR)));
        }

        /**
         * Whether the user has marked this element as reviewed and correct. Only a user-authored
         * scribble counts; the mark shields the element's whole subtree from checks and fixes.
         */
        public boolean isVerified() {
            if (toolAuthored()) return false;
            return segments().stream().anyMatch(seg -> seg.trim().equals(VERIFIED_TOKEN));
        }

        /** The first segment asking the tool to act, or null when none does. */
        public String instruction() {
            return segments().stream()
                    .filter(seg -> seg.startsWith(INSTRUCTION_MARK))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * This scribble with a tool segment appended. Appending to {@link #EMPTY} makes a
         * tool-authored scribble; an existing scribble keeps its own authorship, so a tool note on
         * a hand-written scribble does not claim the whole thing for the tool. A segment already
         * present is not added again, so a fix that runs twice does not stamp itself twice.
         */
        public Scribble with(String segment) {
            if (isEmpty()) return tool(segment);
            if (segments().contains(segment)) return this;
            return rebuilt(body() + SEPARATOR + segment);
        }

        /**
         * This scribble less every segment whose head is {@code head}; {@link #EMPTY} if none
         * remain.
         */
        public Scribble without(String head) {
            List<String> kept =
                    segments().stream().filter(seg -> !headOf(seg).equals(head)).toList();
            if (kept.size() == segments().size()) return this;
            return rebuilt(String.join(SEPARATOR, kept));
        }

        /** The same authorship over a new body. */
        private Scribble rebuilt(String body) {
            return toolAuthored() ? tool(body) : user(body);
        }

        /** The first whitespace-separated token of a segment. */
        private static String headOf(String segment) {
            int sp = segment.indexOf(' ');
            return (sp < 0) ? segment : segment.substring(0, sp);
        }

        @Override
        public String toString() {
            return isEmpty() ? "" : "\"" + rawValue() + "\"";
        }
    }

    /**
     * A table cell's {@code /Scope} attribute, rendered as {@code /Scope /Column}. Read from the
     * {@code /Table}-owned attribute dictionary, the only owner for which {@code /Scope} is
     * defined.
     */
    record Scope(String value) implements DocValue {
        /** Reads the element's Table-owned /Scope, or null when it carries none. */
        public static Scope of(PdfStructElem elem) {
            PdfObject value = Tag.of(elem).attribute(PdfName.Table, PdfName.Scope);
            return value instanceof PdfName name ? new Scope(name.getValue()) : null;
        }

        @Override
        public String toString() {
            return "/Scope /" + value;
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
