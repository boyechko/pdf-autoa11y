// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import java.net.URI;

/** Helpers for reading and validating Link annotation data. */
public final class Link {

    private Link() {}

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
