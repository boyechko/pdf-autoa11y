// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Map;

/** Utilities for clearing or replacing the /RoleMap dictionary under /StructTreeRoot. */
public final class RoleMap {
    private RoleMap() {}

    /** Removes /RoleMap from /StructTreeRoot. Returns the number of entries that were cleared. */
    public static int clear(PdfDocument pdfDoc) {
        PdfDictionary rootDict = requireStructTreeRoot(pdfDoc).getPdfObject();
        PdfDictionary existing = rootDict.getAsDictionary(PdfName.RoleMap);
        if (existing == null) {
            return 0;
        }
        int count = existing.size();
        rootDict.remove(PdfName.RoleMap);
        return count;
    }

    /**
     * Replaces /RoleMap with the supplied mappings. An empty mapping removes /RoleMap entirely.
     *
     * @return true when the structure tree root was updated
     */
    public static boolean replace(PdfDocument pdfDoc, Map<String, String> mappings) {
        PdfDictionary rootDict = requireStructTreeRoot(pdfDoc).getPdfObject();
        if (mappings.isEmpty()) {
            rootDict.remove(PdfName.RoleMap);
            return true;
        }

        PdfDictionary roleMap = new PdfDictionary();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            roleMap.put(asPdfName(entry.getKey()), asPdfName(entry.getValue()));
        }
        rootDict.put(PdfName.RoleMap, roleMap);
        return true;
    }

    /** Returns true when the document has a /RoleMap entry. */
    public static boolean hasRoleMap(PdfDocument pdfDoc) {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        return root != null && root.getPdfObject().containsKey(PdfName.RoleMap);
    }

    private static PdfStructTreeRoot requireStructTreeRoot(PdfDocument pdfDoc) {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
        if (root == null) {
            throw new IllegalStateException("PDF has no structure tree");
        }
        return root;
    }

    private static PdfName asPdfName(String token) {
        return new PdfName(normalizeNameToken(token));
    }

    private static String normalizeNameToken(String token) {
        String trimmed = token.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Role names must not be blank");
        }
        return trimmed;
    }
}
