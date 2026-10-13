// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDocument;

/** Provides access to the document and its structure. */
public class DocContext {
    private final PdfDocument doc;
    private final PageContent pages;
    private boolean dirty = false;

    /**
     * Marks the document as mutated. Checks/fixes that write to PDF objects as a side effect
     * (rather than via the Issue/Fix accounting model — e.g. scribble clearing) should call this so
     * the save gate knows the output differs from the input.
     */
    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public DocContext(PdfDocument doc) {
        this.doc = doc;
        this.pages = new PageContent(doc);
    }

    public PdfDocument doc() {
        return doc;
    }

    /** Per-page marked-content facts, extracted lazily and shared across checks and fixes. */
    public PageContent pages() {
        return pages;
    }
}
