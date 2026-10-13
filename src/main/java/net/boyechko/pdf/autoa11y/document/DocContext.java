// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDocument;

/** Provides access to the document and its structure. */
public class DocContext {
    private final PdfDocument doc;
    private final PageContent pages;
    private final int scopeObjNum;
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
        this(doc, 0);
    }

    /**
     * Creates a context whose tree traversals are confined to the subtree rooted at {@code
     * scopeObjNum} (0 for the whole document). Only structure-tree checks honor the scope;
     * document-level checks see the whole file regardless.
     */
    public DocContext(PdfDocument doc, int scopeObjNum) {
        this.scopeObjNum = scopeObjNum;
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

    /** Object number of the element traversals are confined to, or 0 for the whole tree. */
    public int scopeObjNum() {
        return scopeObjNum;
    }
}
