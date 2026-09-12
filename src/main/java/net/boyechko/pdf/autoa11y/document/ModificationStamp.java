// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.core.VersionInfo;

/**
 * Records that this tool modified a document, in two places: a tool-authored timestamp scribbled on
 * the Document element, e.g. {@code "__:STAMP 2026-08-21 Fri 12:01"}, and a note appended to the
 * {@code /Creator} document-info entry.
 *
 * <p>Apply this at the point the output document is written, so the stamp means the file was
 * actually produced rather than merely opened. Both marks replace any earlier one, so repeated runs
 * leave a single stamp rather than a pile of them.
 */
public final class ModificationStamp {

    /** Segment head identifying the stamp among a scribble's other segments. */
    private static final String TAG = "STAMP";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm", Locale.ENGLISH);

    private static final String CREATOR_NOTE = "modified using PDF-AutoA11y";

    /** Matches a previously appended creator note, with or without a trailing version. */
    private static final Pattern CREATOR_NOTE_SUFFIX =
            Pattern.compile("\\s*;\\s*" + Pattern.quote(CREATOR_NOTE) + "[^;]*$");

    private ModificationStamp() {}

    public static void apply(PdfDocument doc) {
        apply(doc, LocalDateTime.now());
    }

    /** Stamps {@code when} on the document's structure tree and document info. */
    public static void apply(PdfDocument doc, LocalDateTime when) {
        stampStructureTree(doc, when);
        stampDocumentInfo(doc);
    }

    // == Structure tree stamp =========================================

    /** Writes the timestamp scribble, or does nothing if there is no element to carry it. */
    private static void stampStructureTree(PdfDocument doc, LocalDateTime when) {
        PdfStructElem target = stampTarget(doc.getStructTreeRoot());
        if (target == null) {
            return;
        }
        StructTree.clearScribbleSegments(target, TAG);
        StructTree.addToolScribble(target, TAG + " " + TIMESTAMP.format(when));
    }

    /** Returns the Document element, falling back to the first structure element under the root. */
    private static PdfStructElem stampTarget(PdfStructTreeRoot root) {
        if (root == null) {
            return null;
        }
        PdfStructElem document = StructTree.findDocument(root);
        if (document != null) {
            return document;
        }
        List<IStructureNode> kids = root.getKids();
        if (kids == null) {
            return null;
        }
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem elem) {
                return elem;
            }
        }
        return null;
    }

    // == Document info stamp ==========================================

    /**
     * Appends the tool's note to {@code /Creator}, preserving the authoring application that wrote
     * the original. {@code /Producer} is not used: iText overwrites it with its own string on every
     * write, so a note left there would not survive.
     */
    private static void stampDocumentInfo(PdfDocument doc) {
        String existing = doc.getDocumentInfo().getCreator();
        String note = CREATOR_NOTE + versionSuffix();
        if (existing == null || existing.isBlank()) {
            doc.getDocumentInfo().setCreator(note);
            return;
        }
        String original = CREATOR_NOTE_SUFFIX.matcher(existing).replaceAll("").strip();
        doc.getDocumentInfo().setCreator(original.isEmpty() ? note : original + "; " + note);
    }

    /**
     * Returns the running version as {@code " v1.2.3"}, or empty when the build could not identify
     * a release. The release version is used rather than the exact build version, so a PDF records
     * which release touched it without carrying a commit hash.
     */
    private static String versionSuffix() {
        String version = VersionInfo.current().releaseVersion();
        return "dev".equals(version) ? "" : " v" + version;
    }
}
