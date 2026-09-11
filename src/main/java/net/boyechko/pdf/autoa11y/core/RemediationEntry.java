// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.core;

import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;

/**
 * One element-level change made by a fix, snapshotted at the moment it was applied.
 *
 * <p>Values are copied out eagerly rather than held as a live {@link
 * com.itextpdf.kernel.pdf.tagging.PdfStructElem} reference: by the time the log is written, every
 * pipeline step's document is closed.
 *
 * @param objNum PDF object number of the element, or null for document-wide fixes
 * @param pageNum 1-based page number, or null when the fix has no page
 * @param role structure role as it stood when the fix ran, or null if unknown
 * @param fixClass simple class name of the fix that made the change
 * @param message the fix's own description of what it did
 */
public record RemediationEntry(
        Integer objNum, Integer pageNum, String role, String fixClass, String message) {

    /** Snapshots a successfully applied fix while the document is still open. */
    public static RemediationEntry capture(Issue issue, DocContext ctx) {
        IssueLoc where = issue.where();
        Integer objNum = where.objNum();
        return new RemediationEntry(
                objNum,
                resolvePage(where, objNum, ctx),
                where.role(),
                fixNameOf(issue.fix()),
                issue.resolution() != null ? issue.resolution().message() : issue.fix().describe());
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

    /** Falls back to the object-to-page mapping when the location carries no page of its own. */
    private static Integer resolvePage(IssueLoc where, Integer objNum, DocContext ctx) {
        Integer pageNum = where.page();
        if (pageNum != null || objNum == null) {
            return pageNum;
        }
        int mapped = ctx.getPageNumber(objNum);
        return mapped > 0 ? mapped : null;
    }
}
