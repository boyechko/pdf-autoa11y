// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.issue;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;

/**
 * Immutable snapshot of an issue's location, fix identity, and resolution message.
 *
 * <p>Values are copied out eagerly rather than held as a live {@link
 * com.itextpdf.kernel.pdf.tagging.PdfStructElem} reference: by the time the log is written, every
 * pipeline step's document is closed. The remediation pipeline currently captures only issues whose
 * fixes ran successfully, excluding skipped, failed, and unresolved issues. A snapshot describes
 * the issue and its reported resolution, not every object changed by its fix.
 *
 * @param objNum PDF object number from the issue's location, or null if absent
 * @param pageNum 1-based page number from the issue's location or document mapping, or null if
 *     unknown
 * @param role structure role from the issue's location, or null if unknown
 * @param fixClass associated fix class name, or null when no automatic fix exists
 * @param message original detection message
 * @param status remediation outcome; see {@link IssueStatus}
 * @param resolution outcome message, or null while the issue is unresolved
 */
public record IssueSnapshot(
        Integer objNum,
        Integer pageNum,
        String role,
        String fixClass,
        String message,
        IssueStatus status,
        String resolution) {

    /**
     * Copies the issue's location, fix identity, and outcome while the document is still open. The
     * issue must have a non-null fix; callers capture it immediately after successful application.
     */
    public static IssueSnapshot capture(Issue issue, DocContext ctx) {
        Locus where = issue.where();
        Integer objNum = where.objNum();
        return new IssueSnapshot(
                objNum,
                resolvePage(where, objNum, ctx),
                where.role(),
                fixNameOf(issue.fix()),
                issue.message(),
                issue.status(),
                issue.resolution() != null ? issue.resolution().message() : null);
    }

    /**
     * Names the fix class. Several checks declare their fix anonymously, and an anonymous class has
     * a blank simple name, so those fall back to the enclosing check's name.
     */
    private static String fixNameOf(IssueFix fix) {
        Class<?> fixClass = fix.getClass();
        String name = fixClass.getSimpleName();
        if (!name.isEmpty()) {
            return name;
        }
        Class<?> enclosing = fixClass.getEnclosingClass();
        return enclosing != null ? enclosing.getSimpleName() : fixClass.getName();
    }

    /** Falls back to the structure element's page when the location carries none of its own. */
    private static Integer resolvePage(Locus where, Integer objNum, DocContext ctx) {
        Integer pageNum = where.page();
        if (pageNum != null || objNum == null) {
            return pageNum;
        }
        if (ctx.doc().getPdfObject(objNum) instanceof PdfDictionary dict
                && PdfStructElem.isStructElem(dict)) {
            int found = StructTree.pageOf(new PdfStructElem(dict), ctx);
            return found > 0 ? found : null;
        }
        return null;
    }
}
