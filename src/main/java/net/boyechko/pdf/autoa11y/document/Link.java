// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.net.URI;
import java.util.List;

/** Helpers for reading and validating Link annotation data. */
public final class Link {

    private Link() {}

    /**
     * Returns the destination that most precisely identifies a Link annotation's target: the Web
     * Capture /PA URI when present, since it retains the fragment that a resolved /GoTo collapses
     * into a bare page number. Falls back to the /A or /Dest destination, and is null when neither
     * resolves.
     */
    public static DocValue.Destination effectiveDestinationOf(PdfObjRef objRef) {
        DocValue.Destination originalUri = DocValue.originalUriOf(objRef);
        return originalUri != null ? originalUri : DocValue.destinationOf(objRef);
    }

    /**
     * Returns true if every element is a Link whose OBJRs all resolve to one and the same
     * destination — the signature of a single logical link that an authoring tool split across
     * lines, rather than a list whose items point at different targets. Returns false whenever a
     * destination cannot be resolved, so callers fall back to their default behavior instead of
     * acting on missing evidence.
     */
    public static boolean allShareOneDestination(List<PdfStructElem> elems) {
        if (elems.size() < 2) return false;

        DocValue.Destination shared = null;
        for (PdfStructElem elem : elems) {
            if (!PdfName.Link.equals(elem.getRole())) return false;

            List<PdfObjRef> objRefs = StructTree.childrenOf(elem, PdfObjRef.class);
            if (objRefs.isEmpty()) return false;

            for (PdfObjRef objRef : objRefs) {
                DocValue.Destination dest = effectiveDestinationOf(objRef);
                if (dest == null) return false;
                if (shared == null) {
                    shared = dest;
                } else if (!shared.equals(dest)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Returns the URI of a Link annotation's URI action, or null if none. */
    public static String getUri(PdfDictionary annotDict) {
        PdfDictionary action = annotDict.getAsDictionary(PdfName.A);
        if (action != null) {
            PdfName actionType = action.getAsName(PdfName.S);
            if (PdfName.URI.equals(actionType)) {
                var uriObj = action.get(PdfName.URI);
                if (uriObj != null) {
                    return uriObj.toString();
                }
            }
        }
        return null;
    }

    /**
     * Returns true if the string is a plausible http(s) URL with a letters-only TLD of length >= 2.
     */
    public static boolean isValidWebUri(String uri) {
        if (uri == null || uri.isBlank()) return false;
        URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = parsed.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String host = parsed.getHost();
        if (host == null || host.isBlank()) return false;
        int lastDot = host.lastIndexOf('.');
        if (lastDot < 0 || lastDot == host.length() - 1) return false;
        String tld = host.substring(lastDot + 1);
        if (tld.length() < 2) return false;
        for (int i = 0; i < tld.length(); i++) {
            if (!Character.isLetter(tld.charAt(i))) return false;
        }
        return true;
    }
}
